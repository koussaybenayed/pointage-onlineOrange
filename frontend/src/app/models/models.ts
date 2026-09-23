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
  acquittementDate?: string | null;
  acquittementUser?: string | null;
  createdUser?: string | null;
  typeProduit?: string | null;
  acquittementDateTime?: string | null;
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

export interface TicketEvolutionDto {
  dates: string[];
  totalPerDay: number[];
  perUser: Record<string, number[]>;
  bySource: Record<string, number[]>;
  perUserBySource?: Record<string, Record<string, number[]>>;
}

export interface OfficialMember {
  fullName: string;
  email: string;
  team: 'B2B' | 'GP';
}

export const OFFICIAL_MEMBERS: OfficialMember[] = [
  // Equipe B2B
  { fullName: 'Afef Ayari', email: 'afef.ayari@pointage.tn', team: 'B2B' },
  { fullName: 'Lobna Hammami', email: 'lobna.hammami@pointage.tn', team: 'B2B' },
  { fullName: 'Nabil Thouri', email: 'nabil.dhouir@pointage.tn', team: 'B2B' },
  { fullName: 'Med Ali Essifi', email: 'medali.essifi@pointage.tn', team: 'B2B' },
  { fullName: 'Mohamed Ferjene', email: 'mohamed.ferjene@pointage.tn', team: 'B2B' },
  { fullName: 'Fatma Mejri', email: 'fatma.mejri@pointage.tn', team: 'B2B' },
  // Equipe GP
  { fullName: 'Aamer Hammami', email: 'aamer.hammami@pointage.tn', team: 'GP' },
  { fullName: 'Mehdi Cheffi', email: 'mehdi.cheffi@pointage.tn', team: 'GP' },
  { fullName: 'Yosr Gharbi', email: 'yosr.gharbi@pointage.tn', team: 'GP' },
  { fullName: 'Mohamed Ben Marzouk', email: 'mohamed.benmarzouk@pointage.tn', team: 'GP' },
];

export const B2B_MEMBERS = OFFICIAL_MEMBERS.filter(m => m.team === 'B2B');
export const GP_MEMBERS = OFFICIAL_MEMBERS.filter(m => m.team === 'GP');

export interface PlainteSlice {
  label: string;
  count: number;
}

export interface PlainteFileInfo {
  name: string;
  label: string;
}

export interface PlainteOverview {
  files: PlainteFileInfo[];
  stats: Record<string, PlainteSlice[]>;
  recurrent: PlainteRecurrent[];
}

export interface PlainteRecurrent {
  raisonSociale: string;
  count: number;
  firstDate: string;
  lastDate: string;
}

export interface PlainteSearchResult {
  statut: string;
  ticketNumber: string;
  dateOuverture: string | null;
  raisonSociale: string;
  produit: string;
  clientSkills: string;
  service: string;
  priorite: string;
  etat: string;
  reparePar: string;
  responsabilite: string;
}
