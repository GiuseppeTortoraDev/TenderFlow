import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-auth',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './auth.component.html'
})
export class AuthComponent {
  private authService = inject(AuthService);

  isLoginMode = signal(true);
  isLoading = signal(false);
  errorMessage = signal('');

  formData = {
    fullName: '',
    email: '',
    password: '',
    role: 'BUYER'
  };

  toggleMode(): void {
    this.isLoginMode.update((mode) => !mode);
    this.errorMessage.set('');
  }

  onSubmit(): void {
    this.isLoading.set(true);
    this.errorMessage.set('');

    const request$ = this.isLoginMode()
      ? this.authService.login({ email: this.formData.email, password: this.formData.password })
      : this.authService.register(this.formData);

    request$.subscribe({
      next: () => {
        this.isLoading.set(false);
      },
      error: (err) => {
        this.isLoading.set(false);
        this.errorMessage.set(err.error?.error || 'Errore di autenticazione. Verifica i dati.');
      }
    });
  }
}