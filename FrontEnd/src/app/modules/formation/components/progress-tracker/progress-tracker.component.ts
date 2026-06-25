import { Component, input, computed } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-progress-tracker',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './progress-tracker.component.html',
  styleUrl: './progress-tracker.component.scss'
})
export class ProgressTrackerComponent {
  progression = input.required<number>();
  summary = input<string>(); // AI feedback summary
  improvementDelta = input<number>(); // Delta from previous evaluation

  protected readonly Math = Math;

  progressPercentage = computed(() => {
    const value = this.progression();
    return Math.min(Math.max(value, 0), 100);
  });

  progressClass = computed(() => {
    const progress = this.progressPercentage();
    if (progress === 0) return 'empty';
    if (progress < 25) return 'low';
    if (progress < 50) return 'medium-low';
    if (progress < 75) return 'medium-high';
    return 'high';
  });

  deltaClass = computed(() => {
    const delta = this.improvementDelta();
    if (delta === undefined) return '';
    return delta >= 0 ? 'positive' : 'negative';
  });

  deltaIcon = computed(() => {
    const delta = this.improvementDelta();
    if (delta === undefined) return '';
    return delta >= 0 ? '↗' : '↘';
  });
}
