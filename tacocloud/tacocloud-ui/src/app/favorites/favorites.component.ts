import { Component, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';

// TC-21: los favoritos se leen del servidor, así que se conservan al recargar.
@Component({
  selector: 'taco-favorites',
  templateUrl: 'favorites.component.html'
})
export class FavoritesComponent implements OnInit {
  favorites: any[] = [];

  constructor(private httpClient: HttpClient) { }

  ngOnInit() {
    this.load();
  }

  load() {
    this.httpClient.get('http://localhost:8080/api/v1/users/me/favorites')
        .subscribe((page: any) => this.favorites = page.content);
  }

  remove(favorite: any) {
    this.httpClient.delete('http://localhost:8080/api/v1/users/me/favorites/' + favorite.tacoId)
        .subscribe(() => this.load());
  }
}
