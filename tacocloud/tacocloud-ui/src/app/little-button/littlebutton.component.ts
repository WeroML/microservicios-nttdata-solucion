import { Component, OnInit, Input, Output, EventEmitter } from '@angular/core';

@Component({
  selector: 'little-button',
  templateUrl: 'littlebutton.component.html',
  styleUrls: ['./littlebutton.component.css']
})

export class LittleButtonComponent implements OnInit {

  @Input() label: String;
  @Output() action = new EventEmitter();

  constructor() { }

  ngOnInit() { }

  onClick() {
    this.action.emit();
  }
}
