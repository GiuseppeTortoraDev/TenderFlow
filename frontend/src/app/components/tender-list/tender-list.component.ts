import { Component, OnInit, inject, signal, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TenderService } from '../../services/tender.service';
import { BidService } from '../../services/bid.service';
import { AuthService } from '../../services/auth.service';
import { EventStreamService } from '../../services/event-stream.service';
import { Tender } from '../../models/tender.model';
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
  isLoadingBids = signal<boolean>(false);

  // Stato form offerta (SUPPLIER)
  bidAmount: number | null = null;
  isSubmittingBid = signal<boolean>(false);
  actionFeedback = signal<{ type: 'success' | 'danger'; text: string } | null>(null);

  // Stato aggiudicazione (BUYER)
  isClosingTender = signal<boolean>(false);
  awardResult = signal<TenderAwardResult | null>(null);

  constructor() {
    // Se arriva un evento BID_EVENT via Kafka SSE per la gara aperta, aggiorna le offerte in diretta
    effect(() => {
      const notif = this.eventStream.latestNotification();
      const current = this.selectedTender();
      if (notif?.type === 'BID_EVENT' && current?.id && notif.tenderId === current.id) {
        this.loadBids(current.id);
      }
    });
  }

  ngOnInit(): void {
    this.tenderService.loadTenders();
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
      return;
    }

    this.selectedTender.set(tender);
    this.bidAmount = null;
    this.actionFeedback.set(null);
    this.awardResult.set(null);

    if (tender.id) {
      this.loadBids(tender.id);
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

  onSubmitBid(): void {
    const tender = this.selectedTender();
    if (!tender?.id || !this.bidAmount || this.bidAmount <= 0) {
      return;
    }

    this.isSubmittingBid.set(true);
    this.actionFeedback.set(null);

    this.bidService.submitBid(tender.id, this.bidAmount).subscribe({
      next: () => {
        this.isSubmittingBid.set(false);
        this.bidAmount = null;
        this.actionFeedback.set({
          type: 'success',
          text: 'Offerta inviata con successo!'
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
}