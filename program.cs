using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.IdentityModel.Tokens;
using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Text;

var builder = WebApplication.CreateBuilder(args);
var key = "SuperSecretKey_ChangeMe_32chars!";

// AC7: JWT auth setup
builder.Services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddJwtBearer(o => o.TokenValidationParameters = new TokenValidationParameters
    {
        ValidateIssuerSigningKey = true,
        IssuerSigningKey = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(key)),
        ValidateIssuer = false,
        ValidateAudience = false
    });
builder.Services.AddAuthorization();

var app = builder.Build();

// AC8: Global error handler — returns { error } JSON, no stack trace
app.UseExceptionHandler(e => e.Run(async ctx =>
{
    ctx.Response.ContentType = "application/json";
    ctx.Response.StatusCode = 500;
    await ctx.Response.WriteAsJsonAsync(new { error = "An unexpected error occurred." });
}));

app.UseAuthentication();
app.UseAuthorization();

// In-memory users (BCrypt hashed) — AC5
var users = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase)
{
    { "admin", BCrypt.Net.BCrypt.HashPassword("admin123") }
};

// AC1: POST /auth/login accepts { username, password }
app.MapPost("/auth/login", (LoginRequest req, ILogger<Program> logger) =>
{
    // AC4: blank input → 400
    if (string.IsNullOrWhiteSpace(req.Username) || string.IsNullOrWhiteSpace(req.Password))
        return Results.BadRequest(new { error = "Username and password are required." });

    // AC3: wrong credentials → 401; AC6: log failure
    if (!users.TryGetValue(req.Username, out var hash) || !BCrypt.Net.BCrypt.Verify(req.Password, hash))
    {
        logger.LogWarning("Failed login: {Username}", req.Username);
        return Results.Unauthorized();
    }

    // AC2: return signed JWT expiring in 1 hour
    var claims = new[] { new Claim(ClaimTypes.Name, req.Username) };
    var creds  = new SigningCredentials(new SymmetricSecurityKey(Encoding.UTF8.GetBytes(key)), SecurityAlgorithms.HmacSha256);
    var token  = new JwtSecurityToken(claims: claims, expires: DateTime.UtcNow.AddHours(1), signingCredentials: creds);
    return Results.Ok(new { token = new JwtSecurityTokenHandler().WriteToken(token), expiresAt = DateTime.UtcNow.AddHours(1) });
});

// AC7: protected route — 401 if no valid JWT
app.MapGet("/protected", (ClaimsPrincipal user) =>
    Results.Ok(new { message = $"Hello, {user.Identity?.Name}!" })
).RequireAuthorization();

app.Run();

record LoginRequest(string Username, string Password);
