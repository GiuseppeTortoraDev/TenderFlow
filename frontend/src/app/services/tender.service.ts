import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { AuditLogEntry, Tender, TenderStats } from '../models/tender.model';

export interface TenderFilters {
  title?: string;
  status?: string;
  minBudget?: number | null;
  maxBudget?: number | null;
  page: number;
  size: number;
  sortBy: string;
  sortOrder: 'asc' | 'desc';
}

@Injectable({
  providedIn: 'root'
})
export class TenderService {
  private http = inject(HttpClient);
  private readonly apiUrl = 'api/tenders';

  tenders = signal<Tender[]>([]);
  stats = signal<TenderStats | null>(null);
  isLoading = signal<boolean>(false);
  errorMessage = signal<string>('');

  filters = signal<TenderFilters>({
    title: '',
    status: '',
    minBudget: null,
    maxBudget: null,
    page: 0,
    size: 5,
    sortBy: 'id',
    sortOrder: 'desc'
  });

  loadStats(): void {
    this.http.get<TenderStats>(`${this.apiUrl}/stats`).subscribe({
      next: (data) => this.stats.set(data),
      error: (err) => console.error('Errore caricamento KPI:', err)
    });
  }

  loadTenders(customFilters?: Partial<TenderFilters>): void {
    if (customFilters) {
      this.filters.update((prev) => ({ ...prev, ...customFilters }));
    }

    const current = this.filters();
    let params = new HttpParams()
      .set('page', current.page.toString())
      .set('size', current.size.toString())
      .set('sortBy', current.sortBy)
      .set('sortOrder', current.sortOrder);

    if (current.title && current.title.trim()) {
      params = params.set('title', current.title.trim());
    }
    if (current.status) {
      params = params.set('status', current.status);
    }
    if (current.minBudget !== null && current.minBudget !== undefined) {
      params = params.set('minBudget', current.minBudget.toString());
    }
    if (current.maxBudget !== null && current.maxBudget !== undefined) {
      params = params.set('maxBudget', current.maxBudget.toString());
    }

    this.isLoading.set(true);
    this.errorMessage.set('');

    this.http.get<Tender[]>(this.apiUrl, { params }).subscribe({
      next: (data) => {
        this.tenders.set(data);
        this.isLoading.set(false);
        this.loadStats();
      },
      error: (err) => {
        console.error('Errore recupero gare:', err);
        this.errorMessage.set('Errore nel caricamento delle gare.');
        this.isLoading.set(false);
      }
    });
  }

  createTender(tender: Tender): Observable<Tender> {
    return this.http.post<Tender>(this.apiUrl, tender).pipe(
      tap(() => {
        this.loadTenders({ page: 0 });
      })
    );
  }

  publishTender(id: number): Observable<Tender> {
    return this.http.put<Tender>(`${this.apiUrl}/${id}/publish`, {}).pipe(
      tap((updated) => {
        this.tenders.update((current) =>
          current.map((t) => (t.id === id ? updated : t))
        );
        this.loadStats();
      })
    );
  }

  closeTender(id: number): Observable<any> {
    return this.http.put<any>(`${this.apiUrl}/${id}/close`, {}).pipe(
      tap((res) => {
        this.tenders.update((current) =>
          current.map((t) =>
            t.id === id
              ? {
                  ...t,
                  status: 'CLOSED',
                  winningBidderName: res?.winningBid?.bidderName ?? t.winningBidderName,
                  winningAmount: res?.winningBid?.amount ?? t.winningAmount
                }
              : t
          )
        );
        this.loadStats();
      })
    );
  }

  uploadDocument(tenderId: number, file: File): Observable<Tender> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<Tender>(`${this.apiUrl}/${tenderId}/document`, formData);
  }

  getDocumentUrl(tenderId: number): string {
    return `${this.apiUrl}/${tenderId}/document`;
  }

  getAuditLogs(tenderId: number): Observable<AuditLogEntry[]> {
    return this.http.get<AuditLogEntry[]>(`${this.apiUrl}/${tenderId}/audit`);
  }

  downloadCsvReport(tenderId: number): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/${tenderId}/report.csv`, {
      responseType: 'blob'
    });
  }
}