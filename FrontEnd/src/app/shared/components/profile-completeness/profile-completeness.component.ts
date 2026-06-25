import { Component, Input, OnChanges, SimpleChanges, inject } from '@angular/core';

import { ProfileResponse } from '../../../modules/auth/models/user.model';
import { ProfileCompletenessService, CompletenessResult, MissingField } from '../../../core/services/profile-completeness.service';

@Component({
  selector: 'app-profile-completeness',
  standalone: true,
  imports: [],
  template: `
    <div class="completeness-card">
      <div class="completeness-top">
        <!-- SVG circular progress -->
        <div class="ring-wrap">
          <svg viewBox="0 0 120 120" class="ring-svg">
            <circle cx="60" cy="60" r="52" class="ring-bg" />
            <circle cx="60" cy="60" r="52"
              class="ring-progress"
              [style.stroke]="ringColor"
              [style.stroke-dasharray]="circumference"
              [style.stroke-dashoffset]="dashOffset" />
          </svg>
          <div class="ring-label">
            <span class="ring-score">{{ result.score }}</span>
            <span class="ring-percent">%</span>
          </div>
        </div>
        <div class="completeness-info">
          <h4>Profil complété</h4>
          <p class="completeness-detail">{{ result.filledCount }} / {{ result.totalCount }} champs remplis</p>
          @if (result.score >= 100) {
          <p class="completeness-msg success">✅ Profil complet ! Excellent travail.</p>
          } @else if (result.score >= 80) {
          <p class="completeness-msg success">🎉 Très bon profil, encore quelques détails !</p>
          } @else if (result.score >= 50) {
          <p class="completeness-msg warning">💡 Continuez à renseigner votre profil !</p>
          } @else {
          <p class="completeness-msg danger">📝 Complétez votre profil pour de meilleures recommandations.</p>
          }
        </div>
      </div>

      @if (result.missing.length > 0) {
      <div class="missing-fields">
        <span class="missing-label">Champs manquants :</span>
        <div class="missing-chips">
          @for (field of result.missing; track field.key) {
          <button class="missing-chip" (click)="scrollToField(field)">
            + {{ field.label }}
          </button>
          }
        </div>
      </div>
      }

      @if (result.tips && result.tips.length > 0 && result.score < 100) {
      <div class="tips-section">
        <span class="tips-label">💡 Conseils :</span>
        @for (tip of result.tips.slice(0, 2); track tip) {
        <p class="tip-item">{{ tip }}</p>
        }
      </div>
      }
    </div>
  `,

  styles: [`
    .completeness-card {
      background: var(--bg-card);
      border: 1px solid var(--border-light);
      border-radius: 14px;
      padding: 1.25rem;
      box-shadow: var(--shadow-sm);
      margin-bottom: 1.25rem;
      animation: fadeIn 0.3s ease;
    }

    @keyframes fadeIn {
      from { opacity: 0; transform: translateY(8px); }
      to { opacity: 1; transform: translateY(0); }
    }

    .completeness-top {
      display: flex;
      align-items: center;
      gap: 1.25rem;
    }

    .ring-wrap {
      position: relative;
      width: 90px;
      height: 90px;
      flex-shrink: 0;
    }

    .ring-svg {
      width: 100%;
      height: 100%;
      transform: rotate(-90deg);
    }

    .ring-bg {
      fill: none;
      stroke: var(--border, #e2e8f0);
      stroke-width: 8;
    }

    .ring-progress {
      fill: none;
      stroke-width: 8;
      stroke-linecap: round;
      transition: stroke-dashoffset 0.8s cubic-bezier(0.4, 0, 0.2, 1);
    }

    .ring-label {
      position: absolute;
      inset: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 1px;
    }

    .ring-score {
      font-size: 1.375rem;
      font-weight: 800;
      color: var(--text-primary);
      line-height: 1;
    }

    .ring-percent {
      font-size: 0.75rem;
      font-weight: 700;
      color: var(--text-muted);
      margin-top: 2px;
    }

    .completeness-info {
      flex: 1;
    }

    .completeness-info h4 {
      font-size: 0.9375rem;
      font-weight: 700;
      color: var(--text-primary);
      margin: 0 0 0.25rem;
    }

    .completeness-detail {
      font-size: 0.8125rem;
      color: var(--text-secondary);
      margin: 0 0 0.375rem;
    }

    .completeness-msg {
      font-size: 0.8125rem;
      font-weight: 500;
      margin: 0;
    }

    .completeness-msg.success { color: var(--success); }
    .completeness-msg.warning { color: var(--warning); }
    .completeness-msg.danger { color: var(--danger); }

    .missing-fields {
      margin-top: 0.875rem;
      padding-top: 0.75rem;
      border-top: 1px solid var(--border-light);
    }

    .missing-label, .tips-label {
      font-size: 0.75rem;
      font-weight: 600;
      color: var(--text-muted);
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }

    .missing-chips {
      display: flex;
      flex-wrap: wrap;
      gap: 0.375rem;
      margin-top: 0.5rem;
    }

    .missing-chip {
      background: var(--primary-bg);
      color: var(--primary);
      border: 1px solid var(--primary-border);
      border-radius: 999px;
      padding: 0.25rem 0.75rem;
      font-size: 0.75rem;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.15s ease;
    }

    .missing-chip:hover {
      background: var(--primary);
      color: white;
      transform: translateY(-1px);
    }

    .tips-section {
      margin-top: 0.875rem;
      padding: 0.75rem;
      background: #fffbeb;
      border-radius: 10px;
      border: 1px solid #fde68a;
    }

    .tip-item {
      margin: 0.35rem 0 0;
      font-size: 0.8125rem;
      color: #92400e;
      line-height: 1.4;
    }

    @media (max-width: 480px) {
      .completeness-top {
        flex-direction: column;
        text-align: center;
      }
    }
  `]
})
export class ProfileCompletenessComponent implements OnChanges {
  @Input() profile: ProfileResponse | null = null;

  private completenessService = inject(ProfileCompletenessService);

  result: CompletenessResult = { score: 0, filledCount: 0, totalCount: 10, missing: [], tips: [] };

  readonly circumference = 2 * Math.PI * 52; // r=52

  get dashOffset(): number {
    return this.circumference - (this.result.score / 100) * this.circumference;
  }

  get ringColor(): string {
    if (this.result.score >= 80) return 'var(--success, #22c55e)';
    if (this.result.score >= 50) return 'var(--warning, #f59e0b)';
    return 'var(--danger, #ef4444)';
  }

  ngOnChanges(_changes: SimpleChanges): void {
    this.result = this.completenessService.compute(this.profile);
  }

  scrollToField(field: MissingField): void {
    if (!field.inputId) return;
    const el = document.getElementById(field.inputId);
    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'center' });
      el.focus?.();
    }
  }
}
