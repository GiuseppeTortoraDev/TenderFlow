import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TenderService } from '../../services/tender.service';

@Component({
  selector: 'app-analytics-dashboard',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div *ngIf="tenderService.stats() as s" class="mb-4">
      <!-- Riga 4 Card KPI -->
      <div class="row g-3 mb-3">
        <div class="col-6 col-lg-3">
          <div class="card border-0 shadow-sm rounded-3 h-100 border-start border-4 border-primary">
            <div class="card-body p-3">
              <div class="text-uppercase text-muted fw-bold" style="font-size: 0.7rem;">Gare Attive (OPEN)</div>
              <div class="fs-4 fw-bold text-dark mt-1">{{ s.openTenders }} <span class="fs-6 fw-normal text-muted">/ {{ s.totalTenders }}</span></div>
              <div class="text-secondary" style="font-size: 0.75rem;">
                Budget attivo: <strong>{{ s.totalOpenBudget | currency:'EUR':'symbol':'1.0-0' }}</strong>
              </div>
            </div>
          </div>
        </div>

        <div class="col-6 col-lg-3">
          <div class="card border-0 shadow-sm rounded-3 h-100 border-start border-4 border-success">
            <div class="card-body p-3">
              <div class="text-uppercase text-muted fw-bold" style="font-size: 0.7rem;">Volume Aggiudicato</div>
              <div class="fs-4 fw-bold text-success mt-1">{{ s.totalAwardedVolume | currency:'EUR':'symbol':'1.0-0' }}</div>
              <div class="text-secondary" style="font-size: 0.75rem;">
                Gare chiuse: <strong>{{ s.closedTenders }}</strong> ({{ s.totalBids }} offerte totali)
              </div>
            </div>
          </div>
        </div>

        <div class="col-6 col-lg-3">
          <div class="card border-0 shadow-sm rounded-3 h-100 border-start border-4 border-info">
            <div class="card-body p-3">
              <div class="text-uppercase text-muted fw-bold" style="font-size: 0.7rem;">Risparmio Medio Aste</div>
              <div class="fs-4 fw-bold text-info-emphasis mt-1">{{ s.averageSavingsPercentage | number:'1.1-2' }}%</div>
              <div class="progress mt-1" style="height: 5px;">
                <div class="progress-bar bg-info" [style.width.%]="s.averageSavingsPercentage > 100 ? 100 : s.averageSavingsPercentage"></div>
              </div>
            </div>
          </div>
        </div>

        <div class="col-6 col-lg-3">
          <div class="card border-0 shadow-sm rounded-3 h-100 border-start border-4 border-warning">
            <div class="card-body p-3">
              <div class="text-uppercase text-muted fw-bold" style="font-size: 0.7rem;">Risparmio Totale Ottenuto</div>
              <div class="fs-4 fw-bold text-dark mt-1">{{ s.totalSavingsAmount | currency:'EUR':'symbol':'1.0-0' }}</div>
              <div class="text-secondary" style="font-size: 0.75rem;">
                Differenza base d'asta vs aggiudicazione
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Barra Top Fornitori Aggiudicatari -->
      <div *ngIf="s.topSuppliers.length > 0" class="card border-0 shadow-sm rounded-3">
        <div class="card-body py-2 px-3 d-flex flex-wrap align-items-center gap-3">
          <span class="fw-bold text-uppercase text-secondary" style="font-size: 0.72rem;">🏆 Top Fornitori:</span>
          <div *ngFor="let sup of s.topSuppliers; let i = index" class="d-flex align-items-center gap-1 small">
            <span class="badge rounded-pill" [ngClass]="i === 0 ? 'bg-warning text-dark' : 'bg-light text-dark border'">
              #{{ i + 1 }} {{ sup.bidderName }}
            </span>
            <span class="text-muted" style="font-size: 0.75rem;">
              ({{ sup.wonCount }} {{ sup.wonCount === 1 ? 'gara' : 'gare' }} • {{ sup.totalWonVolume | currency:'EUR':'symbol':'1.0-0' }})
            </span>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AnalyticsDashboardComponent implements OnInit {
  tenderService = inject(TenderService);

  ngOnInit(): void {
    this.tenderService.loadStats();
  }
}