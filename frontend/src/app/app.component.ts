import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthService } from './services/auth.service';
import { EventStreamService } from './services/event-stream.service';
import { TenderService } from './services/tender.service';
import { AuthComponent } from './components/auth/auth.component';
import { TenderFormComponent } from './components/tender-form/tender-form.component';
import { TenderListComponent } from './components/tender-list/tender-list.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, AuthComponent, TenderFormComponent, TenderListComponent],
  templateUrl: './app.component.html'
})
export class AppComponent implements OnInit {
  authService = inject(AuthService);
  eventStream = inject(EventStreamService);
  private tenderService = inject(TenderService);

  ngOnInit(): void {
    this.eventStream.connect((event) => {
      if (event.type === 'TENDER_EVENT' && this.authService.isAuthenticated()) {
        this.tenderService.loadTenders();
      }
    });
  }

  logout(): void {
    this.authService.logout();
  }
}