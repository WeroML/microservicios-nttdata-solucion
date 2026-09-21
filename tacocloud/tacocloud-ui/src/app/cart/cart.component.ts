import { Component, OnInit, Injectable } from '@angular/core';
import { CartService } from './cart-service';
import { HttpClient, HttpHeaders } from '@angular/common/http';

@Component({
  selector: 'taco-cart',
  templateUrl: 'cart.component.html',
  styleUrls: ['./cart.component.css']
})

@Injectable()
export class CartComponent implements OnInit {

  model = {
    deliveryName: '',
    deliveryStreet: '',
    deliveryState: '',
    deliveryZip: '',
    paymentMethodId: '',
    items: [] as any[]
  };

  constructor(private cart: CartService, private httpClient: HttpClient) {
    this.cart = cart;
  }

  ngOnInit() {}

  get cartItems() {
    return this.cart.getItemsInCart();
  }

  get cartTotal() {
    return this.cart.getCartTotal();
  }

  onSubmit() {
    this.cart.getItemsInCart().forEach(cartItem => {
      this.model.items.push({ taco: cartItem.taco, quantity: cartItem.quantity || 1 });
    });

    this.httpClient.post(
        'http://localhost:8080/api/orders',
        this.model, {
            headers: new HttpHeaders().set('Content-type', 'application/json')
                    .set('Accept', 'application/json'),
        }).subscribe(r => this.cart.emptyCart());

    // TODO: Do something after this...navigate to a thank you page or something
  }

}
