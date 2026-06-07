# ProductApi

A minimal ASP.NET Core Web API demonstrating a clean, testable GET endpoint with
filtering. Built as interview prep — small enough to read in a sitting, but
structured the way a real project would be.

## What it shows

- **Controller + Web API** — `[ApiController]`, attribute routing, `ControllerBase`
- **Dependency injection** — a service injected via the constructor and registered
  in `Program.cs` with a **Scoped** lifetime
- **Async action** — `async Task<IActionResult>` with `await`, no thread blocking
- **LINQ filtering** — a conditional `Where` applied only when a query parameter is supplied
- **Standard result** — `Ok(result)` returning `200 OK` with JSON

## Project structure

```
ProductApi/
├── ProductApi.sln
├── .gitignore
├── README.md
└── ProductApi/
    ├── ProductApi.csproj
    ├── Program.cs                       # DI registration + pipeline
    ├── appsettings.json
    ├── Properties/launchSettings.json
    ├── Models/Product.cs                # the data model
    ├── Models/ReportSummary.cs          # DTO for the report result + timing
    ├── Models/CategorySummary.cs        # DTO for the per-category aggregates
    ├── Services/IProductService.cs      # abstraction (what the controller depends on)
    ├── Services/ProductService.cs       # implementation (where the LINQ Where lives)
    ├── Services/IReportService.cs       # two independent async operations
    ├── Services/ReportService.cs        # each op ~1s, for the timing demo
    ├── Controllers/ProductsController.cs # the async GET endpoint
    └── Controllers/ReportController.cs  # sequential vs Task.WhenAll demo
```

## Run it

Requires the .NET 8 SDK (or open `ProductApi.sln` in Visual Studio and press F5).

```bash
cd ProductApi
dotnet run --project ProductApi
```

Then open the Swagger UI (the browser launches automatically) or call:

```
GET /api/products
GET /api/products?category=electronics
GET /api/products/summary
```

### LINQ: GroupBy + aggregate

`/api/products/summary` groups the products by category and returns a count, total,
and average price per group:

```csharp
var summaries = _products
    .GroupBy(p => p.Category)                   // group by a field -> IGrouping<string, Product>
    .Select(g => new CategorySummary            // one result object per group
    {
        Category = g.Key,                       // the grouped-on value
        ProductCount = g.Count(),               // count per group
        TotalValue = g.Sum(p => p.Price),       // sum per group
        AveragePrice = g.Average(p => p.Price)  // average per group
    })
    .OrderByDescending(s => s.TotalValue)
    .ToList();
```

`GroupBy` returns a sequence of groups, each an `IGrouping<TKey, TElement>` where
`g.Key` is the value grouped on and the group itself is enumerable. With EF Core this
translates to a SQL `GROUP BY`. To group on more than one field, use an anonymous key:
`GroupBy(p => new { p.Category, p.InStock })`.

### Async: sequential vs parallel

`ReportController` runs two independent ~1-second operations two different ways, and
returns the elapsed time so you can see the difference:

```
GET /api/report/sequential   ->  ~2000 ms  (await one, THEN await the other)
GET /api/report/parallel     ->  ~1000 ms  (start both, then Task.WhenAll)
```

The rule: if two async calls don't depend on each other, start them both first and
await with `Task.WhenAll` so they overlap. Awaiting one after another runs them in
sequence and wastes time.

```csharp
// sequential — second call starts only after the first finishes (~2s)
int count = await GetProductCountAsync();
decimal value = await GetInventoryValueAsync();

// parallel — both start immediately, then await together (~1s)
Task<int> countTask = GetProductCountAsync();
Task<decimal> valueTask = GetInventoryValueAsync();
await Task.WhenAll(countTask, valueTask);
int count = await countTask;        // already complete, returns instantly
decimal value = await valueTask;
```

## The endpoint

```csharp
[HttpGet]
public async Task<IActionResult> GetProducts([FromQuery] string? category)
{
    var products = await _service.GetProductsAsync(category);
    return Ok(products);
}
```

## Notes for a real database

The in-memory list is a stand-in. With Entity Framework Core you would keep the
query as `IQueryable` and apply the filter **before** materializing, so the
database does the filtering in SQL instead of loading the whole table:

```csharp
IQueryable<Product> query = _db.Products;

if (!string.IsNullOrWhiteSpace(category))
    query = query.Where(p => p.Category == category); // translated to SQL WHERE

return await query.ToListAsync(); // executes here
```

Paging would add `.Skip((page - 1) * size).Take(size)` before `ToListAsync()`.
