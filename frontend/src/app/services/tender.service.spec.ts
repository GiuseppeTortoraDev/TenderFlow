import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { TenderService } from './tender.service';

describe('TenderService', () => {
  let service: TenderService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient()]
    });
    service = TestBed.inject(TenderService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});