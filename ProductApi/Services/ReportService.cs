namespace ProductApi.Services;

public class ReportService : IReportService
{
    // Two INDEPENDENT operations, each ~1 second.
    // Independent = neither needs the other's result, so they can run in parallel.

    public async Task<int> GetProductCountAsync()
    {
        await Task.Delay(1000); // pretend: a slow "SELECT COUNT(*)" query
        return 42;
    }

    public async Task<decimal> GetInventoryValueAsync()
    {
        await Task.Delay(1000); // pretend: a slow "SUM(price * stock)" aggregation
        return 12_999.50m;
    }
}
