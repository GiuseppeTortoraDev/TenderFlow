export interface Tender {
  id?: number;
  title: string;
  description: string;
  budget: number;
  closingDate?: string;
  status: 'DRAFT' | 'OPEN' | 'CLOSED' | 'AWARDED';
  createdByEmail?: string;
  createdByName?: string;
  winningBidderName?: string;
  winningAmount?: number;
  documentOriginalName?: string;
}

export interface TopSupplier {
  bidderName: string;
  wonCount: number;
  totalWonVolume: number;
}

export interface TenderStats {
  totalTenders: number;
  openTenders: number;
  draftTenders: number;
  closedTenders: number;
  totalBids: number;
  totalOpenBudget: number;
  totalAwardedVolume: number;
  totalSavingsAmount: number;
  averageSavingsPercentage: number;
  topSuppliers: TopSupplier[];
}

export interface AuditLogEntry {
  id: number;
  tenderId: number;
  action: string;
  actorEmail: string;
  actorName: string;
  details: string;
  createdAt: string;
}