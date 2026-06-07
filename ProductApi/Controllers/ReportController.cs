using System.Diagnostics;
using Microsoft.AspNetCore.Mvc;
using ProductApi.Models;
using ProductApi.Services;

namespace ProductApi.Controllers;

[ApiController]
[Route("api/[controller]")]
public class ReportController : ControllerBase
{
    private readonly IReportService _service;

    public ReportController(IReportService service)
    {
        _service = service;
    }

    // GET /api/report/sequential  ->  takes ~2000 ms
    //
    // Each 'await' fully completes before the next operation STARTS.
    // The second call doesn't even begin until the first has finished.
    [HttpGet("sequential")]
    public async Task<IActionResult> GetSequential()
    {
        var sw = Stopwatch.StartNew();

        int count = await _service.GetProductCountAsync();        // wait ~1s
        decimal value = await _service.GetInventoryValueAsync();   // THEN wait ~1s

        sw.Stop();

        return Ok(new ReportSummary
        {
            Mode = "sequential",
            ProductCount = count,
            TotalInventoryValue = value,
            ElapsedMilliseconds = sw.ElapsedMilliseconds
        });
    }

    // GET /api/report/parallel  ->  takes ~1000 ms
    //
    // Both operations are STARTED first (calling the method returns a running Task),
    // then we await them together with Task.WhenAll. They overlap in time.
    [HttpGet("parallel")]
    public async Task<IActionResult> GetParallel()
    {
        var sw = Stopwatch.StartNew();

        Task<int> countTask = _service.GetProductCountAsync();         // starts now
        Task<decimal> valueTask = _service.GetInventoryValueAsync();   // starts now

        await Task.WhenAll(countTask, valueTask);                      // wait for both

        // Both tasks are already complete, so these awaits return instantly.
        int count = await countTask;
        decimal value = await valueTask;

        sw.Stop();

        return Ok(new ReportSummary
        {
            Mode = "parallel (Task.WhenAll)",
            ProductCount = count,
            TotalInventoryValue = value,
            ElapsedMilliseconds = sw.ElapsedMilliseconds
        });
    }
}
