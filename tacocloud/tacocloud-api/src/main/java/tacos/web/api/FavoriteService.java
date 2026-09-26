package tacos.web.api;

import java.time.Clock;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;
import tacos.Favorite;
import tacos.api.dto.FavoriteResponse;
import tacos.api.dto.PageResponse;
import tacos.api.error.NotFoundException;
import tacos.data.FavoriteRepository;
import tacos.data.TacoRepository;

/**
 * TC-21: favoritos del usuario autenticado.
 * - PUT es idempotente: el índice único userId+tacoId impide duplicados incluso
 *   en concurrencia; DuplicateKeyException se trata como éxito.
 * - DELETE es idempotente: borrar algo que no está también responde 204.
 * - Favoritos cuyo taco ya no existe se eliminan al listar (huérfanos).
 */
@Service
public class FavoriteService {

    private final FavoriteRepository favoriteRepo;
    private final TacoRepository tacoRepo;
    private final Clock clock;
    private final int maxPageSize;

    public FavoriteService(FavoriteRepository favoriteRepo, TacoRepository tacoRepo, Clock clock,
                           @Value("${tacocloud.api.max-page-size:50}") int maxPageSize) {
        this.favoriteRepo = favoriteRepo;
        this.tacoRepo = tacoRepo;
        this.clock = clock;
        this.maxPageSize = maxPageSize;
    }

    public Mono<Void> add(String userId, String tacoId) {
        return tacoRepo.existsById(tacoId)
            .flatMap(exists -> exists
                ? favoriteRepo.findByUserIdAndTacoId(userId, tacoId)
                    .switchIfEmpty(Mono.defer(() -> favoriteRepo.save(
                        new Favorite(null, userId, tacoId, Instant.now(clock)))))
                    .onErrorResume(DuplicateKeyException.class, e -> Mono.empty())
                    .then()
                : Mono.error(new NotFoundException("TACO_NOT_FOUND", "Taco " + tacoId + " not found.")));
    }

    public Mono<Void> remove(String userId, String tacoId) {
        return favoriteRepo.deleteByUserIdAndTacoId(userId, tacoId);
    }

    public Mono<PageResponse<FavoriteResponse>> list(String userId, int page, int size) {
        Paging.validate(page, size, maxPageSize);
        PageRequest pageRequest = PageRequest.of(page, size,
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("_id")));
        return favoriteRepo.findByUserId(userId, pageRequest)
            .concatMap(favorite -> tacoRepo.findById(favorite.getTacoId())
                .map(taco -> new FavoriteResponse(taco.getId(), taco.getName(), favorite.getCreatedAt()))
                .switchIfEmpty(Mono.defer(() -> favoriteRepo.delete(favorite).then(Mono.empty()))))
            .collectList()
            .flatMap(content -> favoriteRepo.findByUserId(userId, PageRequest.of(page + 1, size))
                .hasElements()
                .map(hasNext -> new PageResponse<>(content, page, size, hasNext)));
    }
}
