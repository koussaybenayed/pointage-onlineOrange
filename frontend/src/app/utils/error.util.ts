import { HttpErrorResponse } from '@angular/common/http';

export function extractError(error: unknown): string {
  if (typeof error === 'string') return error;
  if (error instanceof HttpErrorResponse) {
    const body = error.error as { message?: string } | undefined;
    if (body && body.message) return body.message;
    if (typeof error.error === 'string' && error.error) return error.error;
    if (error.status === 401) return 'Unauthorized. Please log in again.';
    if (error.status === 403) return 'You do not have permission to do this.';
  }
  return 'Something went wrong. Please try again.';
}
