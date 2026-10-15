import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { HistoryComponent } from './history.component';
import { HistoryPage, JourneyDetail } from './history.types';
const user={userId:'user1',employeeId:'employee1',email:'test@example.invalid',name:'Teste',referenceTimeZoneId:'America/Sao_Paulo'};
const item={id:'session1',state:'CLOSED' as const,workDate:'2026-10-14',referenceTimeZoneId:'America/Sao_Paulo',openedAtUtc:'2026-10-14T23:30:00Z',closedAtUtc:'2026-10-15T01:30:00Z',workDuration:'PT2H',provisional:false};
const page: HistoryPage={items:[item],page:0,size:20,totalElements:21,observedAtUtc:'2026-10-15T02:00:00Z'};
const detail: JourneyDetail={session:item,observedAtUtc:page.observedAtUtc,events:[{id:'event1',sequence:1,type:'ENTRY',occurredAtUtc:item.openedAtUtc,recordedAtUtc:item.openedAtUtc,recordedTimeZoneId:'America/Sao_Paulo'}]};
describe('History and detail',()=>{
  let fixture: ComponentFixture<HistoryComponent>; let http: HttpTestingController;
  const component=()=>fixture.componentInstance;
  beforeEach(async()=>{
    await TestBed.configureTestingModule({imports:[HistoryComponent],providers:[provideHttpClient(),provideHttpClientTesting()]}).compileComponents();
    http=TestBed.inject(HttpTestingController);fixture=TestBed.createComponent(HistoryComponent);fixture.componentRef.setInput('user',user);fixture.detectChanges();
    component().form.setValue({from:'2026-10-01',to:'2026-10-31'});
  });
  afterEach(()=>{fixture.destroy();http.verify();});
  function consult(){component().consult();http.expectOne(r=>r.url==='/api/me/work-sessions').flush(page);fixture.detectChanges();}
  it('identifies the selected HR employee in history cards and details and clears the old name on selection change',()=>{
    fixture.componentRef.setInput('targetEmployee',{id:'brasil',name:'Colaborador Brasil',referenceTimeZoneId:'America/Sao_Paulo'});fixture.detectChanges();
    component().form.setValue({from:'2026-10-01',to:'2026-10-31'});
    component().consult();http.expectOne(r=>r.url==='/api/hr/employees/brasil/work-sessions').flush(page);fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.journeys .history-person').textContent).toContain('Colaborador Brasil');
    component().open(item.id);http.expectOne('/api/hr/employees/brasil/work-sessions/session1').flush(detail);fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.history-person').length).toBe(2);
    for(const label of fixture.nativeElement.querySelectorAll('.history-person')) expect(label.textContent).toContain('Colaborador Brasil');
    fixture.componentRef.setInput('targetEmployee',{id:'europa',name:'Colaborador Europa',referenceTimeZoneId:'Europe/Amsterdam'});fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('Colaborador Brasil');
    expect(fixture.nativeElement.querySelector('.history-person').textContent).toContain('Colaborador Europa');
    expect(component().detail()).toBeNull();
  });
  it('validates the inclusive range without querying and prevents duplicate queries',()=>{
    component().form.setValue({from:'2026-10-31',to:'2026-10-01'});component().consult();expect(component().fieldError()).toContain('366');http.expectNone('/api/me/work-sessions');
    component().form.setValue({from:'2026-10-01',to:'2026-10-31'});component().consult();component().consult();
    const request=http.expectOne(r=>r.url==='/api/me/work-sessions');expect(request.request.params.get('from')).toBe('2026-10-01');request.flush(page);
  });
  it('preserves filters and views the same event in Sao Paulo and Lisbon',()=>{
    consult();
    const original=component().formatInstant(item.openedAtUtc,item.referenceTimeZoneId);expect(original).toContain('14/10/2026, 20:30:00 GMT-3');component().zone.setValue('Europe/Lisbon');
    expect(component().formatInstant(item.openedAtUtc,item.referenceTimeZoneId)).toContain('15/10/2026, 00:30:00 GMT+1');expect(component().result()?.items[0].workDate).toBe('2026-10-14');
    component().form.controls.from.setValue('2026-09-01');component().load(1);
    const request=http.expectOne(r=>r.url==='/api/me/work-sessions');expect(request.request.params.get('from')).toBe('2026-10-01');expect(request.request.params.get('page')).toBe('1');request.flush({...page,page:1});
  });
  it('shows event detail and preserves the result when returning',()=>{
    consult();component().open(item.id);http.expectOne('/api/me/work-sessions/session1').flush(detail);fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Marcações originais');expect(fixture.nativeElement.textContent).not.toContain('Fuso no registro:');
    component().back();expect(component().result()?.totalElements).toBe(21);expect(component().applied()?.from).toBe('2026-10-01');
  });
  it('shows the recording zone of each differing event without changing reference date',()=>{
    consult();component().open(item.id);
    http.expectOne('/api/me/work-sessions/session1').flush({...detail,events:[
      {...detail.events[0],recordedTimeZoneId:'Europe/Lisbon'},
      {...detail.events[0],id:'event2',sequence:2,type:'EXIT'}
    ]});fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Fuso no registro: Europe/Lisbon');
    expect(fixture.nativeElement.textContent).not.toContain('Fuso no registro: America/Sao_Paulo');
    expect(component().detail()?.session.workDate).toBe('2026-10-14');
    expect(component().detail()?.session.referenceTimeZoneId).toBe('America/Sao_Paulo');
  });
  it('clears stale results on failed new consultation and offers a retry',()=>{
    consult();component().consult();http.expectOne(r=>r.url==='/api/me/work-sessions').flush({}, {status:503,statusText:'Unavailable'});
    expect(component().result()).toBeNull();expect(component().message()).toContain('preservados');expect(component().busy()).toBe(false);
  });
  it('cancels an old identity request and removes its data on account change',()=>{
    component().consult();const request=http.expectOne(r=>r.url==='/api/me/work-sessions');fixture.componentRef.setInput('user',{...user,userId:'other',employeeId:'other'});fixture.detectChanges();
    expect(request.cancelled).toBe(true);expect(component().result()).toBeNull();expect(component().applied()).toBeNull();
  });
  it('explains empty periods',()=>{
    component().consult();http.expectOne(r=>r.url==='/api/me/work-sessions').flush({...page,items:[],totalElements:0});fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Nenhuma jornada');expect(fixture.nativeElement.textContent).not.toContain('Total de trabalho encerrado');
  });
});
