namespace ProductApi.Models;

public class ReportSummary
{
    public string Mode { get; set; } = string.Empty;
    public int ProductCount { get; set; }
    public decimal TotalInventoryValue { get; set; }
    public long ElapsedMilliseconds { get; set; }
}
