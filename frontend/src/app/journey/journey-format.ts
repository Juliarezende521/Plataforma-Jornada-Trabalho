export function formatInstant(instant: string, zone: string): string {
  try { return new Intl.DateTimeFormat('pt-BR', { timeZone: zone, day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit', second: '2-digit', timeZoneName: 'shortOffset' }).format(new Date(instant)); }
  catch { return 'Horário indisponível'; }
}
export function formatWorkDate(date: string): string { return date.split('-').reverse().join('/'); }
export function formatDuration(duration: string): string {
  const match = /^PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+(?:\.\d+)?)S)?$/.exec(duration);
  if (!match) return 'Duração indisponível';
  const total = Number(match[1] ?? 0) * 3600 + Number(match[2] ?? 0) * 60 + Number(match[3] ?? 0);
  return `${Math.floor(total / 3600)} h ${Math.floor(total % 3600 / 60).toString().padStart(2, '0')} min`;
}
