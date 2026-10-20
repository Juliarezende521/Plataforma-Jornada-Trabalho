import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AdjustmentReviewComponent } from './adjustment-review.component';

const item={id:'00000000-0000-0000-0000-000000000020',workSessionId:'s',employeeId:'e',employeeName:'Colaboradora',requestedEventType:'EXIT' as const,proposedOccurredAtUtc:'2026-10-19T21:00:00Z',reason:'Saída não registrada no horário.',status:'PENDING' as const,requestedAtUtc:'2026-10-20T12:00:00Z',decidedAtUtc:null,decisionNote:null};
describe('AdjustmentReviewComponent',()=>{
  let fixture:ComponentFixture<AdjustmentReviewComponent>;let http:HttpTestingController;
  beforeEach(async()=>{
    await TestBed.configureTestingModule({imports:[AdjustmentReviewComponent],providers:[provideHttpClient(),provideHttpClientTesting()]}).compileComponents();
    http=TestBed.inject(HttpTestingController);fixture=TestBed.createComponent(AdjustmentReviewComponent);fixture.detectChanges();
  });
  afterEach(()=>{fixture.destroy();http.verify();vi.restoreAllMocks();});
  it('requires confirmation, sends an audited HR decision and refreshes the queue',()=>{
    http.expectOne('/api/hr/work-adjustments?page=0&size=20').flush({items:[item],page:0,size:20,totalElements:1});
    fixture.componentInstance.analyze(item);fixture.componentInstance.note.setValue('Conferido com a liderança.');
    vi.spyOn(window,'confirm').mockReturnValue(true);fixture.componentInstance.decide('APPROVED');
    http.expectOne('/api/auth/csrf').flush({headerName:'X-CSRF-TOKEN',token:'token'});
    const decision=http.expectOne('/api/hr/work-adjustments/00000000-0000-0000-0000-000000000020/decision');
    expect(decision.request.body).toEqual({decision:'APPROVED',note:'Conferido com a liderança.'});
    decision.flush({...item,status:'APPROVED',decidedAtUtc:'2026-10-20T12:10:00Z',decisionNote:'Conferido com a liderança.'});
    http.expectOne('/api/hr/work-adjustments?page=0&size=20').flush({items:[],page:0,size:20,totalElements:0});
    expect(window.confirm).toHaveBeenCalled();
  });
  it('does not decide when confirmation is cancelled',()=>{
    http.expectOne('/api/hr/work-adjustments?page=0&size=20').flush({items:[item],page:0,size:20,totalElements:1});
    fixture.componentInstance.analyze(item);vi.spyOn(window,'confirm').mockReturnValue(false);
    fixture.componentInstance.decide('REJECTED');http.expectNone('/api/auth/csrf');
  });
});
