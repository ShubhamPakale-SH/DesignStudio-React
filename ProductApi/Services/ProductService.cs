using ProductApi.Models;

namespace ProductApi.Services;

public class ProductService : IProductService
{
    // Stand-in data source. In a real app this would be a DbContext / repository.
    private readonly List<Product> _products = new()
    {
        new() { Id = 1, Name = "Keyboard",   Category = "Electronics", Price = 49.99m  },
        new() { Id = 2, Name = "Coffee Mug", Category = "Kitchen",     Price = 9.99m   },
        new() { Id = 3, Name = "Monitor",    Category = "Electronics", Price = 199.99m },
        new() { Id = 4, Name = "Notebook",   Category = "Stationery",  Price = 4.50m   },
    };

    public async Task<IEnumerable<Product>> GetProductsAsync(string? category)
    {
        await Task.Delay(10); // simulate async I/O (e.g. a database call)

        var query = _products.AsEnumerable();

        // Filter only if a category was supplied
        if (!string.IsNullOrWhiteSpace(category))
            query = query.Where(p =>
                p.Category.Equals(category, StringComparison.OrdinalIgnoreCase));

        return query.ToList();
    }

    public async Task<IEnumerable<CategorySummary>> GetCategorySummariesAsync()
    {
        await Task.Delay(10); // simulate async I/O

        var summaries = _products
            .GroupBy(p => p.Category)                   // group rows by the Category field
            .Select(g => new CategorySummary            // project ONE summary per group
            {
                Category = g.Key,                       // the value we grouped on
                ProductCount = g.Count(),               // COUNT per group
                TotalValue = g.Sum(p => p.Price),       // SUM per group
                AveragePrice = g.Average(p => p.Price)  // AVG per group
            })
            .OrderByDescending(s => s.TotalValue)
            .ToList();

        return summaries;
    }
}
