import { Component, Input, OnChanges } from '@angular/core';


export interface RadarSlice {
  label: string;
  value: number; // 0-10
}

@Component({
  selector: 'app-radar-chart',
  standalone: true,
  imports: [],
  template: `
    <div class="radar-container" [style.width.px]="size" [style.height.px]="size">
      <svg [attr.viewBox]="'0 0 ' + size + ' ' + size">
        <!-- Background Polygons -->
        @for (ring of [1, 0.8, 0.6, 0.4, 0.2]; track ring) {
          <polygon
            [attr.points]="getPoints(ring * 10)"
            class="grid-ring"
            />
        }
    
        <!-- Axis Lines -->
        @for (axis of axes; track axis.label; let i = $index) {
          <line
            [attr.x1]="center" [attr.y1]="center"
            [attr.x2]="getAxisX(i)" [attr.y2]="getAxisY(i)"
            class="axis-line"
            />
        }
    
        <!-- Data Polygon -->
        <polygon [attr.points]="dataPoints" class="data-area" />
        @for (p of dataDots; track $index) {
          <circle [attr.cx]="p.x" [attr.cy]="p.y" r="4" class="data-dot" />
        }
    
        <!-- Labels -->
        @for (axis of axes; track axis.label; let i = $index) {
          <text
            [attr.x]="getLabelX(i)"
            [attr.y]="getLabelY(i)"
            [attr.text-anchor]="getTextAnchor(i)"
            class="label"
            >
            {{ axis.label }}
          </text>
        }
      </svg>
    </div>
    `,
  styles: [`
    .radar-container { margin: 0 auto; }
    .grid-ring { fill: none; stroke: #e2e8f0; stroke-width: 1; }
    .axis-line { stroke: #f1f5f9; stroke-width: 1; }
    .data-area { fill: rgba(79, 70, 229, 0.2); stroke: #4f46e5; stroke-width: 3; stroke-linejoin: round; }
    .data-dot { fill: #4f46e5; }
    .label { font-size: 11px; font-weight: 700; fill: #64748b; font-family: sans-serif; text-transform: uppercase; }
  `]
})
export class RadarChartComponent implements OnChanges {
  @Input() axes: RadarSlice[] = [];
  @Input() size = 300;

  center = 150;
  radius = 110;
  dataPoints = '';
  dataDots: {x: number, y: number}[] = [];

  ngOnChanges(): void {
    this.center = this.size / 2;
    this.radius = (this.size / 2) * 0.75;
    this.calculateData();
  }

  private calculateData(): void {
    if (!this.axes.length) return;
    const angleStep = (Math.PI * 2) / this.axes.length;
    
    const points = this.axes.map((axis, i) => {
      const r = (axis.value / 10) * this.radius;
      const x = this.center + r * Math.cos(i * angleStep - Math.PI / 2);
      const y = this.center + r * Math.sin(i * angleStep - Math.PI / 2);
      return { x, y };
    });

    this.dataPoints = points.map(p => `${p.x},${p.y}`).join(' ');
    this.dataDots = points;
  }

  getPoints(value: number): string {
    const angleStep = (Math.PI * 2) / this.axes.length;
    const r = (value / 10) * this.radius;
    return this.axes.map((_, i) => {
      const x = this.center + r * Math.cos(i * angleStep - Math.PI / 2);
      const y = this.center + r * Math.sin(i * angleStep - Math.PI / 2);
      return `${x},${y}`;
    }).join(' ');
  }

  getAxisX(i: number): number {
    const angleStep = (Math.PI * 2) / this.axes.length;
    return this.center + this.radius * Math.cos(i * angleStep - Math.PI / 2);
  }

  getAxisY(i: number): number {
    const angleStep = (Math.PI * 2) / this.axes.length;
    return this.center + this.radius * Math.sin(i * angleStep - Math.PI / 2);
  }

  getLabelX(i: number): number {
    const angleStep = (Math.PI * 2) / this.axes.length;
    const r = this.radius + 20;
    return this.center + r * Math.cos(i * angleStep - Math.PI / 2);
  }

  getLabelY(i: number): number {
    const angleStep = (Math.PI * 2) / this.axes.length;
    const r = this.radius + 20;
    return this.center + r * Math.sin(i * angleStep - Math.PI / 2);
  }

  getTextAnchor(i: number): string {
    const angle = (i / this.axes.length) * 360;
    if (angle === 0 || angle === 180) return 'middle';
    if (angle > 0 && angle < 180) return 'start';
    return 'end';
  }
}
