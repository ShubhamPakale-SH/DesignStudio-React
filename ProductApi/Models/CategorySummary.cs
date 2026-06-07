namespace ProductApi.Models;

public class CategorySummary
{
    public string Category { get; set; } = string.Empty;
    public int ProductCount { get; set; }
    public decimal TotalValue { get; set; }
    public decimal AveragePrice { get; set; }
}
