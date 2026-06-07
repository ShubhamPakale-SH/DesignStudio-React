namespace ProductApi.Services;

public interface IReportService
{
    Task<int> GetProductCountAsync();
    Task<decimal> GetInventoryValueAsync();
}
