using System.Drawing;
using System.Drawing.Drawing2D;
using System.Runtime.InteropServices;
using System.Windows.Forms;
using VXV.RuneBridge.Pathfinding;

namespace VXV.RuneBridge.Debug;

/// <summary>
/// Temporary top-most transparent overlay that draws GDI arrows at screen positions
/// for a few seconds (debug aid for FindNearest).
/// </summary>
public static class DebugTileOverlay
{
    public static void ShowArrows(
        IEnumerable<(ScreenPoint Point, string? Label)> markers,
        TimeSpan? duration = null)
    {
        if (!RuntimeInformation.IsOSPlatform(OSPlatform.Windows))
        {
            return;
        }

        var list = markers
            .Where(m => m.Point.OnScreen)
            .Select(m => (m.Point.X, m.Point.Y, m.Label ?? ""))
            .ToList();

        if (list.Count == 0)
        {
            return;
        }

        var life = duration ?? TimeSpan.FromSeconds(3);
        // STA thread required for WinForms / GDI window
        var thread = new Thread(() => RunOverlay(list, life))
        {
            IsBackground = true,
            Name = "RuneBridge-DebugOverlay"
        };
        thread.SetApartmentState(ApartmentState.STA);
        thread.Start();
    }

    public static void ShowArrowsForTiles(
        IEnumerable<(WorldTile Tile, string? Label)> tiles,
        TimeSpan? duration = null)
    {
        var markers = new List<(ScreenPoint, string?)>();
        foreach (var (tile, label) in tiles)
        {
            var sp = tile.ToScreen();
            if (sp is ScreenPoint p && p.OnScreen)
            {
                markers.Add((p, label ?? tile.ToString()));
            }
        }
        ShowArrows(markers, duration);
    }

    private static void RunOverlay(List<(int X, int Y, string Label)> points, TimeSpan life)
    {
        try
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);

            using var form = new OverlayForm(points);
            var timer = new System.Windows.Forms.Timer { Interval = (int)Math.Max(100, life.TotalMilliseconds) };
            timer.Tick += (_, _) =>
            {
                timer.Stop();
                form.Close();
            };
            timer.Start();
            Application.Run(form);
        }
        catch
        {
            // Debug-only; swallow failures (no window, headless, etc.)
        }
    }

    private sealed class OverlayForm : Form
    {
        private readonly List<(int X, int Y, string Label)> _points;

        public OverlayForm(List<(int X, int Y, string Label)> points)
        {
            _points = points;
            FormBorderStyle = FormBorderStyle.None;
            ShowInTaskbar = false;
            TopMost = true;
            StartPosition = FormStartPosition.Manual;
            Bounds = Screen.PrimaryScreen?.Bounds ?? new Rectangle(0, 0, 1920, 1080);
            // Per-pixel alpha layered window
            BackColor = Color.Magenta;
            TransparencyKey = Color.Magenta;
            Opacity = 1.0;
            DoubleBuffered = true;

            // Click-through
            int ex = GetWindowLong(Handle, GwlExstyle);
            SetWindowLong(Handle, GwlExstyle, ex | WsExLayered | WsExTransparent | WsExToolwindow | WsExTopmost);
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            base.OnPaint(e);
            e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
            e.Graphics.Clear(Color.Magenta);

            using var pen = new Pen(Color.FromArgb(240, 255, 64, 64), 3f);
            using var brush = new SolidBrush(Color.FromArgb(230, 255, 80, 80));
            using var textBrush = new SolidBrush(Color.White);
            using var textBg = new SolidBrush(Color.FromArgb(180, 0, 0, 0));
            using var font = new Font("Segoe UI", 9f, FontStyle.Bold);

            foreach (var (x, y, label) in _points)
            {
                // Arrow pointing down at the target pixel
                int tipX = x;
                int tipY = y;
                int topY = tipY - 36;
                e.Graphics.DrawLine(pen, tipX, topY, tipX, tipY - 6);
                Point[] arrow =
                {
                    new(tipX, tipY),
                    new(tipX - 8, tipY - 14),
                    new(tipX + 8, tipY - 14),
                };
                e.Graphics.FillPolygon(brush, arrow);
                e.Graphics.DrawPolygon(pen, arrow);

                if (!string.IsNullOrEmpty(label))
                {
                    var size = e.Graphics.MeasureString(label, font);
                    float tx = tipX - size.Width / 2f;
                    float ty = topY - size.Height - 2;
                    e.Graphics.FillRectangle(textBg, tx - 2, ty - 1, size.Width + 4, size.Height + 2);
                    e.Graphics.DrawString(label, font, textBrush, tx, ty);
                }
            }
        }

        protected override CreateParams CreateParams
        {
            get
            {
                var cp = base.CreateParams;
                cp.ExStyle |= WsExLayered | WsExTransparent | WsExToolwindow | WsExTopmost;
                return cp;
            }
        }

        private const int GwlExstyle = -20;
        private const int WsExLayered = 0x80000;
        private const int WsExTransparent = 0x20;
        private const int WsExToolwindow = 0x80;
        private const int WsExTopmost = 0x8;

        [DllImport("user32.dll")]
        private static extern int GetWindowLong(IntPtr hWnd, int nIndex);

        [DllImport("user32.dll")]
        private static extern int SetWindowLong(IntPtr hWnd, int nIndex, int dwNewLong);
    }
}
