import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { TenderFormComponent } from './tender-form.component';

describe('TenderFormComponent', () => {
  let component: TenderFormComponent;
  let fixture: ComponentFixture<TenderFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TenderFormComponent],
      providers: [provideHttpClient()]
    }).compileComponents();

    fixture = TestBed.createComponent(TenderFormComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});