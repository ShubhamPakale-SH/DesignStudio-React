using ProductApi.Models;

namespace ProductApi.Services;

public interface IProductService
{
    Task<IEnumerable<Product>> GetProductsAsync(string? category);
    Task<IEnumerable<CategorySummary>> GetCategorySummariesAsync();
}
