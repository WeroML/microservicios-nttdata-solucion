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
    deliveryCity: '',
    deliveryState: '',
    deliveryZip: '',
    paymentMethodId: '',
    items: [] as any[]
  };

  paymentMethods: any[] = [];
  placedOrder: any;

  constructor(private cart: CartService, private httpClient: HttpClient) {
    this.cart = cart;
  }

  // TC-12: sólo métodos de pago tokenizados (brand/last4); la UI nunca pide PAN ni CVV.
  ngOnInit() {
    this.httpClient.get('http://localhost:8080/api/v1/payment-methods')
        .subscribe((data: any[]) => {
          this.paymentMethods = data;
          if (data.length > 0) {
            this.model.paymentMethodId = data[0].id;
          }
        });
  }

  get cartItems() {
    return this.cart.getItemsInCart();
  }

  // TC-14: la UI envía tacos (nombre + IDs de ingredientes) y cantidades.
  // Precios y total los calcula el servidor; la respuesta trae el total real.
  onSubmit() {
    this.model.items = this.cart.getItemsInCart()
      .filter(cartItem => Number(cartItem.quantity) > 0)
      .map(cartItem => ({
        taco: {
          name: cartItem.taco.name,
          ingredientIds: cartItem.taco.ingredients.map(ingredient => ingredient.id)
        },
        quantity: Number(cartItem.quantity)
      }));

    this.httpClient.post(
        'http://localhost:8080/api/v1/orders',
        this.model, {
            headers: new HttpHeaders().set('Content-type', 'application/json')
                    .set('Accept', 'application/json'),
        }).subscribe(order => {
          this.placedOrder = order;
          this.cart.emptyCart();
        });
  }

}
