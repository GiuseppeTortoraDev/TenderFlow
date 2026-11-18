import { Injectable, signal, OnDestroy } from '@angular/core';

export interface LiveEvent {
  type: 'TENDER_EVENT' | 'BID_EVENT' | 'PING';
  tenderId: number | null;
  title: string | null;
  message: string;
}

@Injectable({
  providedIn: 'root'
})
export class EventStreamService implements OnDestroy {
  private eventSource: EventSource | null = null;
  private readonly streamUrl = '/api/events/stream';

  isConnected = signal<boolean>(false);
  latestNotification = signal<LiveEvent | null>(null);

  connect(onEventCallback?: (event: LiveEvent) => void): void {
    if (this.eventSource || typeof EventSource === 'undefined') {
      return;
    }

    this.eventSource = new EventSource(this.streamUrl);

    this.eventSource.onopen = () => {
      this.isConnected.set(true);
    };

    this.eventSource.onmessage = (msg) => {
      try {
        const parsed: LiveEvent = JSON.parse(msg.data);
        if (parsed.type === 'PING') {
          return;
        }
        this.latestNotification.set(parsed);
        if (onEventCallback) {
          onEventCallback(parsed);
        }
        setTimeout(() => {
          if (this.latestNotification() === parsed) {
            this.latestNotification.set(null);
          }
        }, 6000);
      } catch (e) {
        console.error('Errore parsing evento SSE:', e);
      }
    };

    this.eventSource.onerror = () => {
      this.isConnected.set(false);
    };
  }

  dismissNotification(): void {
    this.latestNotification.set(null);
  }

  disconnect(): void {
    if (this.eventSource) {
      this.eventSource.close();
      this.eventSource = null;
      this.isConnected.set(false);
    }
  }

  ngOnDestroy(): void {
    this.disconnect();
  }
}