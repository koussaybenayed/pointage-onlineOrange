export interface User {
  id: number;
  email: string;
  fullName: string;
  role: 'USER' | 'ADMIN';
  team: 'B2B' | 'GP';
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  userId: number;
  email: string;
  fullName: string;
  role: 'USER' | 'ADMIN';
  team: 'B2B' | 'GP';
}

export type OnlineDayStatus = 'BOOKED' | 'WORKED' | 'MISSED';

export interface OnlineDay {
  id: number;
  dayDate: string;
  status: OnlineDayStatus;
}

export interface Report {
  id: number;
  onlineDayId: number;
  dayDate: string;
  content: string;
  submittedAt: string;
  userName?: string;
}

export interface UserWeekStats {
  userId: number;
  userName: string;
  bookedDays: number;
  workedDays: number;
  missedDays: number;
  ratio: number;
  rendement: number;
  weekStart: string;
}

export interface OnlineDayAdmin {
  id: number;
  userName: string;
  dayDate: string;
  status: OnlineDayStatus;
}

export interface TicketDto {
  ticketNumber: string;
  userName: string;
  createdDate: string | null;
  resolutionDate: string;
  createdDateTime: string | null;
  resolutionDateTime: string;
  followUpDate: string | null;
  source: string;
}

export interface UserTicketStatsDto {
  userName: string;
  team: string;
  totalTickets: number;
  tickets: TicketDto[];
}

export interface TicketAlert {
  type: string;
  userName: string;
  message: string;
  timestamp: string;
}

export interface TicketReportResponse {
  userStats: UserTicketStatsDto[];
  alerts: TicketAlert[];
}
