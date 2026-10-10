import { TestBed } from '@angular/core/testing';
import { App } from './app';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    })
      .compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('identifies the foundation stage instead of claiming a finished product', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('h1')?.textContent).toContain('Jornada de trabalho');
    expect(compiled.textContent).toContain('ainda não estão disponíveis');
  });

  it('confirms the actual API response', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const button = (fixture.nativeElement as HTMLElement).querySelector('button')!;
    button.click();
    TestBed.inject(HttpTestingController).expectOne('/api/status')
      .flush({ status: 'UP', stage: 'FOUNDATION', journeyAvailable: false });
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Conexão confirmada');
    TestBed.inject(HttpTestingController).verify();
  });

  it('shows a recoverable error when the API is unavailable', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    (fixture.nativeElement as HTMLElement).querySelector('button')!.click();
    TestBed.inject(HttpTestingController).expectOne('/api/status')
      .flush({}, { status: 503, statusText: 'Service Unavailable' });
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('tente novamente');
    TestBed.inject(HttpTestingController).verify();
  });
});
