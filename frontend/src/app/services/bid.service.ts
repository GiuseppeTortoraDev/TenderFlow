import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Bid } from '../models/bid.model';

@Injectable({
  providedIn: 'root'
})
export class BidService {
  private http = inject(HttpClient);
  private readonly baseUrl = 'http://localhost:8081/api/tenders';

  getBidsForTender(tenderId: number): Observable<Bid[]> {
    return this.http.get<Bid[]>(`${this.baseUrl}/${tenderId}/bids?sortBy=amount&sortOrder=asc`);
  }

  submitBid(tenderId: number, amount: number): Observable<Bid> {
    return this.http.post<Bid>(`${this.baseUrl}/${tenderId}/bids`, { amount });
  }
}