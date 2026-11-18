import { Tender } from './tender.model';

export interface Bid {
  id?: number;
  bidderName?: string;
  bidderEmail?: string;
  amount: number;
  submissionDate?: string;
  tender?: Tender;
}

export interface TenderAwardResult {
  tender?: Tender;
  winningBid?: Bid;
  message?: string;
  tenderId?: number;
}