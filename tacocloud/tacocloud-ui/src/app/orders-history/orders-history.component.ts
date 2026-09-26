import { Component, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';

// TC-23: historial paginado y privado (la API usa al usuario autenticado).
@Component({
  selector: 'taco-orders-history',
  templateUrl: 'orders-history.component.html'
})
export class OrdersHistoryComponent implements OnInit {
  orders: any[] = [];
  page = 0;
  hasNext = false;

  constructor(private httpClient: HttpClient) { }

  ngOnInit() {
    this.load(0);
  }

  load(page: number) {
    this.httpClient.get('http://localhost:8080/api/v1/users/me/orders?page=' + page + '&size=10')
        .subscribe((response: any) => {
          this.orders = response.content;
          this.page = response.page;
          this.hasNext = response.hasNext;
        });
  }
}
