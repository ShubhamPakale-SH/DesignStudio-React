using Microsoft.AspNetCore.Mvc;
using ProductApi.Services;

namespace ProductApi.Controllers;

[ApiController]
[Route("api/[controller]")]
public class ProductsController : ControllerBase
{
    private readonly IProductService _service;

    // Constructor injection — the DI container supplies the service
    public ProductsController(IProductService service)
    {
        _service = service;
    }

    // GET /api/products
    // GET /api/products?category=electronics
    [HttpGet]
    public async Task<IActionResult> GetProducts([FromQuery] string? category)
    {
        var products = await _service.GetProductsAsync(category);
        return Ok(products); // 200 OK with the list serialized as JSON
    }

    // GET /api/products/summary
    // Groups products by category and returns count + total + average per group.
    [HttpGet("summary")]
    public async Task<IActionResult> GetCategorySummary()
    {
        var summaries = await _service.GetCategorySummariesAsync();
        return Ok(summaries);
    }
}
