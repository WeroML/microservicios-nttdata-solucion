import { Component, OnInit, Injectable } from '@angular/core';
import { Http } from '@angular/http';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { CartService } from '../cart/cart-service';

@Component({
  selector: 'recent-tacos',
  templateUrl: 'recents.component.html',
  styleUrls: ['./recents.component.css']
})

@Injectable()
export class RecentTacosComponent implements OnInit {
  recentTacos: any;

  constructor(private httpClient: HttpClient, private cart: CartService, private router: Router) { }

  ngOnInit() {
    this.httpClient.get('http://localhost:8080/api/tacos?recent') // <1>
        .subscribe(data => this.recentTacos = data);
  }

  orderTaco(taco: any) {
    this.cart.addToCart(taco);
    this.router.navigate(['/cart']);
  }
}
