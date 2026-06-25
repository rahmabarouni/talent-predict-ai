import { Component, Input } from '@angular/core';


export interface PieChartSlice {
  label: string;
  value: number;
  color: string;
}

@Component({
  selector: 'app-pie-chart',
  standalone: true,
  imports: [],
  templateUrl: './pie-chart.component.html',
  styleUrls: ['./pie-chart.component.scss']
})
export class PieChartComponent {
  @Input() slices: PieChartSlice[] = [];
  @Input() size = 180;
  @Input() strokeWidth = 26;
  @Input() centerLabel = 'Score';
  @Input() centerValue = '';
  @Input() showLegend = true;

  get normalizedSlices(): PieChartSlice[] {
    return this.slices.filter((slice) => Number(slice?.value) > 0);
  }

  get total(): number {
    return this.normalizedSlices.reduce((sum, slice) => sum + Number(slice.value || 0), 0);
  }

  get radius(): number {
    return Math.max(0, (this.size - this.strokeWidth) / 2);
  }

  get circumference(): number {
    return 2 * Math.PI * this.radius;
  }

  get center(): number {
    return this.size / 2;
  }

  get hasData(): boolean {
    return this.total > 0;
  }

  getDashArray(value: number): string {
    if (!this.hasData) {
      return `0 ${this.circumference}`;
    }
    const arc = (Math.max(0, value) / this.total) * this.circumference;
    return `${arc} ${this.circumference - arc}`;
  }

  getDashOffset(index: number): number {
    if (!this.hasData || index <= 0) {
      return 0;
    }

    const previousTotal = this.normalizedSlices
      .slice(0, index)
      .reduce((sum, slice) => sum + slice.value, 0);

    return -((previousTotal / this.total) * this.circumference);
  }

  getPercentage(value: number): number {
    if (!this.hasData) {
      return 0;
    }
    return Math.round((value / this.total) * 100);
  }
}
