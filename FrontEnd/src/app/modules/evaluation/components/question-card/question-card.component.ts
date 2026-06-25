import { Component, Input, Output, EventEmitter } from '@angular/core';

import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-question-card',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './question-card.component.html',
  styleUrls: ['./question-card.component.scss']
})
export class QuestionCardComponent {
  @Input() question = '';
  @Input() questionNumber = 1;
  @Input() category = '';
  @Input() currentAnswer = '';
  @Output() answer = new EventEmitter<string>();

  ratingOptions = [
    { value: '1', label: 'Pas du tout', emoji: '😐' },
    { value: '2', label: 'Peu', emoji: '🙁' },
    { value: '3', label: 'Neutre', emoji: '😶' },
    { value: '4', label: 'Assez', emoji: '🙂' },
    { value: '5', label: 'Tout à fait', emoji: '😊' }
  ];

  selectRating(value: string): void {
    this.answer.emit(value);
  }

  isSelected(value: string): boolean {
    return this.currentAnswer === value;
  }
}
