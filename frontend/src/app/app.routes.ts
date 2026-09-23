import { Routes } from '@angular/router';
import { AuthGuard } from './guards/auth.guard';
import { AdminGuard } from './guards/admin.guard';
import { ShellComponent } from './shell/shell.component';
import { LoginComponent } from './auth/login/login.component';
import { RegisterComponent } from './auth/register/register.component';
import { CalendarComponent } from './calendar/calendar.component';
import { MyStatsComponent } from './my-stats/my-stats.component';
import { AdminDashboardComponent } from './admin/admin-dashboard.component';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },
  {
    path: '',
    component: ShellComponent,
    canActivate: [AuthGuard],
    children: [
      { path: '', redirectTo: 'calendar', pathMatch: 'full' },
      { path: 'calendar', component: CalendarComponent },
      { path: 'stats', component: MyStatsComponent },
      { path: 'admin', component: AdminDashboardComponent, canActivate: [AdminGuard] },
    ],
  },
  { path: '**', redirectTo: 'calendar' },
];