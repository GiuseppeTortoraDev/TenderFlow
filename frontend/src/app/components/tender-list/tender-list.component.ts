import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TenderService } from '../../services/tender.service';

@Component({
  selector: 'app-tender-list',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './tender-list.component.html',
  styleUrl: './tender-list.component.scss'
})
export class TenderListComponent implements OnInit {
  tenderService = inject(TenderService);

  ngOnInit(): void {
    this.tenderService.loadTenders();
  }
}