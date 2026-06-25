import {
  Component,
  inject,
  OnInit,
  AfterViewInit,
  OnDestroy,
  ElementRef,
  PLATFORM_ID
} from '@angular/core';

import { isPlatformBrowser } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../auth/services/auth.service';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss'
})
export class HomeComponent implements OnInit, AfterViewInit, OnDestroy {
  private router = inject(Router);
  private authService = inject(AuthService);
  private hostElement = inject(ElementRef<HTMLElement>);
  private platformId = inject(PLATFORM_ID);
  private observer: IntersectionObserver | null = null;
  private readonly isBrowser = isPlatformBrowser(this.platformId);

  mobileMenuOpen = false;

  ngOnInit(): void {
    if (this.authService.isAuthenticated()) {
      this.router.navigateByUrl(this.authService.getRedirectUrl());
    }
  }

  ngAfterViewInit(): void {
    this.initializeRevealAnimations();
  }

  ngOnDestroy(): void {
    this.observer?.disconnect();
    this.observer = null;
  }

  private initializeRevealAnimations(): void {
    if (!this.isBrowser) {
      return;
    }

    const host = this.hostElement.nativeElement as HTMLElement;
    const revealTargets = Array.from(host.querySelectorAll('[data-reveal]')) as HTMLElement[];

    if (!revealTargets.length) {
      return;
    }

    if (!('IntersectionObserver' in window)) {
      revealTargets.forEach((element) => element.classList.add('visible'));
      return;
    }

    this.observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (!entry.isIntersecting) {
            continue;
          }

          entry.target.classList.add('visible');
          this.observer?.unobserve(entry.target);
        }
      },
      {
        threshold: 0.15,
        rootMargin: '0px 0px -6% 0px'
      }
    );

    revealTargets.forEach((element, index) => {
      element.style.setProperty('--reveal-delay', `${Math.min(index * 70, 450)}ms`);
      this.observer?.observe(element);
    });
  }
}
