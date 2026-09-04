import { Component, OnInit } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthService } from '../services/auth.service';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, CommonModule],
  templateUrl: './shell.component.html',
  styleUrls: ['./shell.component.scss'],
})
export class ShellComponent implements OnInit {
  fullName = '';
  isAdmin = false;

  constructor(private auth: AuthService, private router: Router) {}

  ngOnInit(): void {
    this.fullName = this.auth.user?.fullName ?? '';
    this.isAdmin = this.auth.isAdmin();
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}