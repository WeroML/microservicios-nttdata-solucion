import { Component, OnInit, Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { RecentTacosService } from './RecentTacosService';

@Component({
  selector: 'recent-tacos',
  templateUrl: 'recents.component.html',
  styleUrls: ['./recents.component.css']
})

@Injectable()
export class RecentTacosComponent implements OnInit {
  recentTacos: any;
  scores = {};
  message: string;

  constructor(private recentTacosService: RecentTacosService, private httpClient: HttpClient) { }

  ngOnInit() {
    this.recentTacosService.getRecentTacos() // <1>
        .subscribe(response => this.recentTacos = response.json().content);
  }

  // TC-21: marcar favorito es idempotente (PUT).
  addFavorite(taco: any) {
    this.httpClient.put('http://localhost:8080/api/v1/users/me/favorites/' + taco.id, null)
        .subscribe(() => this.message = '"' + taco.name + '" added to your favorites.');
  }

  // TC-22: el voto es del usuario autenticado; repetirlo lo actualiza.
  rate(taco: any) {
    const score = Number(this.scores[taco.id] || 5);
    this.httpClient.put('http://localhost:8080/api/v1/tacos/' + taco.id + '/rating', { score: score }, {
          headers: new HttpHeaders().set('Content-type', 'application/json')
        })
        .subscribe(() => this.message = 'You rated "' + taco.name + '" with ' + score + '.');
  }
}
