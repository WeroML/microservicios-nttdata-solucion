package tacos.api.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// TC-19/TC-21/TC-23: metadata mínima de paginación.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {
    private List<T> content;
    private int page;
    private int size;
    private boolean hasNext;

    // Se consultan size+1 elementos: si llegó el extra, existe página siguiente.
    public static <T> PageResponse<T> of(List<T> fetched, int page, int size) {
        boolean hasNext = fetched.size() > size;
        List<T> content = hasNext ? fetched.subList(0, size) : fetched;
        return new PageResponse<>(content, page, size, hasNext);
    }
}
