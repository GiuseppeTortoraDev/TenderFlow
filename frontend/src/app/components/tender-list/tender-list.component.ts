import { Component, OnInit, inject, signal, effect, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TenderService } from '../../services/tender.service';
import { BidService } from '../../services/bid.service';
import { AuthService } from '../../services/auth.service';
import { EventStreamService } from '../../services/event-stream.service';
import { Tender, AuditLogEntry } from '../../models/tender.model';
import { Bid, TenderAwardResult } from '../../models/bid.model';

@Component({
  selector: 'app-tender-list',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './tender-list.component.html',
  styleUrl: './tender-list.component.scss'
})
export class TenderListComponent implements OnInit {
  tenderService = inject(TenderService);
  private bidService = inject(BidService);
  authService = inject(AuthService);
  private eventStream = inject(EventStreamService);

  // Campi form filtri
  searchTitle = '';
  filterStatus = '';
  minBudget: number | null = null;
  maxBudget: number | null = null;
  sortOption = 'id:desc';

  // Stato della gara selezionata/espansa
  selectedTender = signal<Tender | null>(null);
  bids = signal<Bid[]>([]);
  auditLogs = signal<AuditLogEntry[]>([]);
  isLoadingBids = signal<boolean>(false);

  // Eventuale offerta già inviata dal SUPPLIER corrente
  myCurrentBid = computed(() => {
    const email = this.authService.currentUser()?.email;
    if (!email) return null;
    return this.bids().find((b) => b.bidderEmail?.toLowerCase() === email.toLowerCase()) ?? null;
  });

  // Stato form offerta (SUPPLIER)
  bidAmount: number | null = null;
  isSubmittingBid = signal<boolean>(false);
  actionFeedback = signal<{ type: 'success' | 'danger'; text: string } | null>(null);

  // Stato pubblicazione / aggiudicazione / upload (BUYER)
  isPublishingTender = signal<boolean>(false);
  isClosingTender = signal<boolean>(false);
  awardResult = signal<TenderAwardResult | null>(null);
  uploadingTenderId = signal<number | null>(null);

  constructor() {
    effect(() => {
      const notif = this.eventStream.latestNotification();
      const current = this.selectedTender();
      if (notif?.type === 'BID_EVENT' && current?.id && notif.tenderId === current.id) {
        this.loadBids(current.id);
        if (this.isTenderOwner(current)) {
          this.loadAuditLogs(current.id);
        }
      }
    });
  }

  ngOnInit(): void {
    this.tenderService.loadTenders();
  }

  isTenderOwner(tender: Tender): boolean {
    const userEmail = this.authService.currentUser()?.email;
    if (!userEmail || this.authService.userRole() !== 'BUYER') {
      return false;
    }
    return !tender.createdByEmail || tender.createdByEmail.toLowerCase() === userEmail.toLowerCase();
  }

  applyFilters(): void {
    const [sortBy, sortOrder] = this.sortOption.split(':') as [string, 'asc' | 'desc'];
    this.tenderService.loadTenders({
      title: this.searchTitle,
      status: this.filterStatus,
      minBudget: this.minBudget,
      maxBudget: this.maxBudget,
      sortBy,
      sortOrder,
      page: 0
    });
  }

  resetFilters(): void {
    this.searchTitle = '';
    this.filterStatus = '';
    this.minBudget = null;
    this.maxBudget = null;
    this.sortOption = 'id:desc';
    this.applyFilters();
  }

  changePage(delta: number): void {
    const nextPage = Math.max(0, this.tenderService.filters().page + delta);
    this.tenderService.loadTenders({ page: nextPage });
  }

  toggleDetails(tender: Tender): void {
    if (this.selectedTender()?.id === tender.id) {
      this.selectedTender.set(null);
      this.auditLogs.set([]);
      return;
    }

    this.selectedTender.set(tender);
    this.bidAmount = null;
    this.actionFeedback.set(null);
    this.awardResult.set(null);

    if (tender.id) {
      this.loadBids(tender.id);
      if (this.isTenderOwner(tender)) {
        this.loadAuditLogs(tender.id);
      } else {
        this.auditLogs.set([]);
      }
    }
  }

  loadBids(tenderId: number): void {
    this.isLoadingBids.set(true);
    this.bidService.getBidsForTender(tenderId).subscribe({
      next: (data) => {
        this.bids.set(data);
        this.isLoadingBids.set(false);
      },
      error: (err) => {
        console.error('Errore recupero offerte:', err);
        this.actionFeedback.set({
          type: 'danger',
          text: err.error?.message || 'Impossibile caricare le offerte.'
        });
        this.isLoadingBids.set(false);
      }
    });
  }

  loadAuditLogs(tenderId: number): void {
    this.tenderService.getAuditLogs(tenderId).subscribe({
      next: (logs) => this.auditLogs.set(logs),
      error: () => this.auditLogs.set([])
    });
  }

  onDownloadCsvReport(tender: Tender): void {
    if (!tender.id) return;
    this.tenderService.downloadCsvReport(tender.id).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `report-gara-${tender.id}.csv`;
        a.click();
        window.URL.revokeObjectURL(url);
      },
      error: () => {
        this.actionFeedback.set({
          type: 'danger',
          text: 'Errore durante la generazione del report CSV.'
        });
      }
    });
  }

  onSubmitBid(): void {
    const tender = this.selectedTender();
    if (!tender?.id || !this.bidAmount || this.bidAmount <= 0) {
      return;
    }

    const wasUpdate = this.myCurrentBid() !== null;
    this.isSubmittingBid.set(true);
    this.actionFeedback.set(null);

    this.bidService.submitBid(tender.id, this.bidAmount).subscribe({
      next: () => {
        this.isSubmittingBid.set(false);
        this.bidAmount = null;
        this.actionFeedback.set({
          type: 'success',
          text: wasUpdate ? 'Offerta migliorata al ribasso con successo!' : 'Offerta inviata con successo!'
        });
        this.loadBids(tender.id!);
      },
      error: (err) => {
        this.isSubmittingBid.set(false);
        this.actionFeedback.set({
          type: 'danger',
          text: err.error?.message || 'Errore durante l\'invio dell\'offerta.'
        });
      }
    });
  }

  onPublishTender(): void {
    const tender = this.selectedTender();
    if (!tender?.id) {
      return;
    }

    this.isPublishingTender.set(true);
    this.actionFeedback.set(null);

    this.tenderService.publishTender(tender.id).subscribe({
      next: (updated) => {
        this.isPublishingTender.set(false);
        this.selectedTender.set(updated);
        this.loadAuditLogs(tender.id!);
        this.actionFeedback.set({
          type: 'success',
          text: 'Gara pubblicata con successo! Ora i fornitori possono inviare offerte.'
        });
      },
      error: (err) => {
        this.isPublishingTender.set(false);
        this.actionFeedback.set({
          type: 'danger',
          text: err.error?.message || 'Errore durante la pubblicazione della gara.'
        });
      }
    });
  }

  onCloseTender(): void {
    const tender = this.selectedTender();
    if (!tender?.id) {
      return;
    }

    this.isClosingTender.set(true);
    this.actionFeedback.set(null);

    this.tenderService.closeTender(tender.id).subscribe({
      next: (res: TenderAwardResult) => {
        this.isClosingTender.set(false);
        this.awardResult.set(res);
        this.selectedTender.set({ ...tender, status: 'CLOSED' });
        this.loadAuditLogs(tender.id!);
        this.actionFeedback.set({
          type: 'success',
          text: res.winningBid
            ? `Gara aggiudicata a ${res.winningBid.bidderName} per € ${res.winningBid.amount}!`
            : (res.message || 'Gara chiusa senza offerte.')
        });
      },
      error: (err) => {
        this.isClosingTender.set(false);
        this.actionFeedback.set({
          type: 'danger',
          text: err.error?.message || 'Errore durante la chiusura della gara.'
        });
      }
    });
  }

  getDocumentUrl(tenderId: number): string {
    return this.tenderService.getDocumentUrl(tenderId);
  }

  onDocumentSelected(event: Event, tender: Tender): void {
    const input = event.target as HTMLInputElement;
    if (!input.files || input.files.length === 0 || !tender.id) {
      return;
    }

    const file = input.files[0];
    if (file.type !== 'application/pdf' && !file.name.toLowerCase().endsWith('.pdf')) {
      alert('Seleziona esclusivamente un file in formato PDF (.pdf).');
      input.value = '';
      return;
    }

    this.uploadingTenderId.set(tender.id);
    this.tenderService.uploadDocument(tender.id, file).subscribe({
      next: (updatedTender) => {
        this.uploadingTenderId.set(null);
        input.value = '';
        this.tenderService.tenders.update((list: Tender[]) =>
          list.map((t: Tender) => (t.id === updatedTender.id ? updatedTender : t))
        );
        if (this.selectedTender()?.id === updatedTender.id) {
          this.selectedTender.set(updatedTender);
          this.loadAuditLogs(updatedTender.id!);
        }
      },
      error: (err) => {
        this.uploadingTenderId.set(null);
        input.value = '';
        const msg = err.error?.message || 'Errore durante il caricamento del capitolato PDF';
        alert(msg);
      }
    });
  }
}