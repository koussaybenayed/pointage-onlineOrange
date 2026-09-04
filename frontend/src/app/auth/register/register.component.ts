import { Component } from '@angular/core';
import { FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { extractError } from '../../utils/error.util';
import { CommonModule } from '@angular/common';
import { AuthShellComponent } from '../auth-shell/auth-shell.component';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, CommonModule, AuthShellComponent],
  templateUrl: './register.component.html',
  styleUrls: ['./register.component.scss'],
})
export class RegisterComponent {
  form: FormGroup<{
    fullName: FormControl<string | null>;
    email: FormControl<string | null>;
    password: FormControl<string | null>;
    confirm: FormControl<string | null>;
  }>;
  loading = false;
  error = '';

  constructor(
    private fb: FormBuilder,
    private auth: AuthService,
    private router: Router
  ) {
    this.form = this.fb.group(
      {
        fullName: ['', [Validators.required, Validators.minLength(3)]],
        email: ['', [Validators.required, Validators.email]],
        password: ['', [Validators.required, Validators.minLength(6)]],
        confirm: ['', [Validators.required]],
      },
      { validators: [this.matchPasswords] }
    );
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading = true;
    this.error = '';
    const { fullName, email, password } = this.form.value;
    this.auth.register({ fullName: fullName!, email: email!, password: password!, asAdmin: false }).subscribe({
      next: (user) => {
        this.loading = false;
        this.router.navigate([user.role === 'ADMIN' ? '/admin' : '/calendar']);
      },
      error: (err) => {
        this.loading = false;
        this.error = extractError(err);
      },
    });
  }

  private matchPasswords(group: { value: { password?: string; confirm?: string } }): { mismatch?: boolean } | null {
    if (group.value.password !== group.value.confirm) return { mismatch: true };
    return null;
  }
}