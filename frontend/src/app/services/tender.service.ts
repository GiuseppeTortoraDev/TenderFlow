import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { Tender } from '../models/tender.model';

@Injectable({
  providedIn: 'root'
})
export class TenderService {
  private http = inject(HttpClient);
  private readonly apiUrl = 'http://localhost:8081/api/tenders';

  // Stato reattivo condiviso tra Form e Lista
  tenders = signal<Tender[]>([]);
  isLoading = signal<boolean>(false);
  errorMessage = signal<string>('');

  loadTenders(): void {
    this.isLoading.set(true);
    this.errorMessage.set('');

    this.http.get<Tender[]>(this.apiUrl).subscribe({
      next: (data) => {
        this.tenders.set(data);
        this.isLoading.set(false);
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
      tap((created) => {
        // Aggiorna istantaneamente la lista in memoria senza reload
        this.tenders.update((current) => [...current, created]);
      })
    );
  }

  closeTender(id: number): Observable<any> {
    return this.http.put<any>(`${this.apiUrl}/${id}/close`, {}).pipe(
      tap(() => {
        this.tenders.update((current) =>
          current.map((t) => (t.id === id ? { ...t, status: 'CLOSED' } : t))
        );
      })
    );
  }
}