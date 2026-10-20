import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AdjustmentPanelComponent } from './adjustment-panel.component';

describe('AdjustmentPanelComponent',()=>{
  let fixture:ComponentFixture<AdjustmentPanelComponent>; let http:HttpTestingController;
  beforeEach(async()=>{
    await TestBed.configureTestingModule({imports:[AdjustmentPanelComponent],providers:[provideHttpClient(),provideHttpClientTesting()]}).compileComponents();
    http=TestBed.inject(HttpTestingController);fixture=TestBed.createComponent(AdjustmentPanelComponent);
    fixture.componentRef.setInput('workSessionId','00000000-0000-0000-0000-000000000010');
    fixture.componentRef.setInput('displayZone','America/Sao_Paulo');fixture.detectChanges();
  });
  afterEach(()=>{fixture.destroy();http.verify();});
  it('loads only the selected journey and submits a validated correction with CSRF',()=>{
    const initial=http.expectOne(r=>r.url==='/api/me/work-adjustments');
    expect(initial.request.params.get('workSessionId')).toBe('00000000-0000-0000-0000-000000000010');
    initial.flush({items:[],page:0,size:20,totalElements:0});
    fixture.componentInstance.form.setValue({requestedEventType:'EXIT',proposedLocalTime:'2026-10-19T18:00',reason:'Esqueci de registrar a saída.'});
    fixture.componentInstance.submit();
    http.expectOne('/api/auth/csrf').flush({headerName:'X-CSRF-TOKEN',token:'token'});
    const post=http.expectOne('/api/me/work-adjustments');
    expect(post.request.headers.get('X-CSRF-TOKEN')).toBe('token');
    expect(post.request.body.workSessionId).toContain('00000010');
    expect(post.request.body.reason).toBe('Esqueci de registrar a saída.');
    post.flush({id:'a',workSessionId:'00000000-0000-0000-0000-000000000010',employeeId:'e',employeeName:'Employee',requestedEventType:'EXIT',proposedOccurredAtUtc:'2026-10-19T21:00:00Z',reason:post.request.body.reason,status:'PENDING',requestedAtUtc:'2026-10-20T12:00:00Z',decidedAtUtc:null,decisionNote:null});
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Solicitação enviada ao RH');
    expect(fixture.nativeElement.textContent).toContain('Aguardando análise');
  });
  it('does not send an invalid short justification',()=>{
    http.expectOne(r=>r.url==='/api/me/work-adjustments').flush({items:[],page:0,size:20,totalElements:0});
    fixture.componentInstance.form.setValue({requestedEventType:'ENTRY',proposedLocalTime:'2026-10-19T09:00',reason:'curta'});
    fixture.componentInstance.submit();http.expectNone('/api/auth/csrf');
  });
});
