import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-auth-shell',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './auth-shell.component.html',
  styleUrls: ['./auth-shell.component.scss'],
})
export class AuthShellComponent {}