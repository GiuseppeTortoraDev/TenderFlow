import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TenderService } from '../../services/tender.service';
import { Tender } from '../../models/tender.model';

@Component({
  selector: 'app-tender-form',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './tender-form.component.html',
  styleUrl: './tender-form.component.scss'
})
export class TenderFormComponent {
  private tenderService = inject(TenderService);

  newTender: Partial<Tender> = {
    title: '',
    description: '',
    budget: undefined,
    closingDate: '',
    status: 'OPEN'
  };

  isSubmitting = signal(false);
  feedbackError = signal('');

  onSubmit(): void {
    if (!this.newTender.title || !this.newTender.description || !this.newTender.budget) {
      return;
    }

    this.isSubmitting.set(true);
    this.feedbackError.set('');

    const payload: Tender = {
      ...(this.newTender as Tender),
      closingDate: this.newTender.closingDate || undefined
    };

    this.tenderService.createTender(payload).subscribe({
      next: () => {
        this.newTender = {
          title: '',
          description: '',
          budget: undefined,
          closingDate: '',
          status: 'OPEN'
        };
        this.isSubmitting.set(false);
      },
      error: (err) => {
        console.error('Errore creazione gara:', err);
        this.feedbackError.set(err.error?.message || `Errore ${err.status}: verifica i dati o il token JWT.`);
        this.isSubmitting.set(false);
      }
    });
  }
}