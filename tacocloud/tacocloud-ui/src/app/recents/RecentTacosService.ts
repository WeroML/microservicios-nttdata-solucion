import { Injectable } from '@angular/core';
import { ApiService } from '../api/ApiService';

@Injectable()
export class RecentTacosService {

  constructor(private apiService: ApiService) {
  }

  // TC-19: búsqueda paginada de la API, ordenada por fecha de creación.
  getRecentTacos() {
    return this.apiService.get('/api/v1/tacos?sort=createdAt,desc&size=12');
  }

}
