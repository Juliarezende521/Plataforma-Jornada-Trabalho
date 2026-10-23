import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { HistoryComponent } from './history.component';
import { HistoryPage, JourneyDetail } from './history.types';
const user={userId:'user1',employeeId:'employee1',email:'test@example.invalid',name:'Teste',referenceTimeZoneId:'America/Sao_Paulo'};
const item={id:'session1',state:'CLOSED' as const,workDate:'2026-10-14',referenceTimeZoneId:'America/Sao_Paulo',openedAtUtc:'2026-10-14T23:30:00Z',closedAtUtc:'2026-10-15T01:30:00Z',workDuration:'PT2H',provisional:false};
const page: HistoryPage={items:[item],page:0,size:20,totalElements:21,observedAtUtc:'2026-10-15T02:00:00Z'};
const summary={from:'2026-10-01',to:'2026-10-31',totalWorkDuration:'PT8H',plannedWorkDuration:'PT7H30M',balanceSeconds:1800,toleranceSeconds:3600,withinTolerance:true,scheduledDays:31,periodDays:31,totalSessions:21,closedSessions:20,openSessions:1,adjustedSessions:2,workedDays:20,observedAtUtc:page.observedAtUtc};
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
  function consult(){component().consult();http.expectOne(r=>r.url==='/api/me/work-sessions').flush(page);http.expectOne('/api/me/work-sessions/summary?from=2026-10-01&to=2026-10-31').flush(summary);fixture.detectChanges();}
  it('identifies the selected HR employee in history cards and details and clears the old name on selection change',()=>{
    fixture.componentRef.setInput('targetEmployee',{id:'brasil',name:'Colaborador Brasil',referenceTimeZoneId:'America/Sao_Paulo'});fixture.detectChanges();
    component().form.setValue({from:'2026-10-01',to:'2026-10-31'});
    component().consult();http.expectOne(r=>r.url==='/api/hr/employees/brasil/work-sessions').flush(page);
    http.expectOne('/api/hr/employees/brasil/work-sessions/summary?from=2026-10-01&to=2026-10-31').flush(summary);fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.journeys .history-person').textContent).toContain('Colaborador Brasil');
    component().open(item.id);http.expectOne('/api/hr/employees/brasil/work-sessions/session1').flush(detail);fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.history-person').length).toBe(2);
    for(const label of fixture.nativeElement.querySelectorAll('.history-person')) expect(label.textContent).toContain('Colaborador Brasil');
    fixture.componentRef.setInput('targetEmployee',{id:'europa',name:'Colaborador Europa',referenceTimeZoneId:'Europe/Amsterdam'});fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('Colaborador Brasil');
    expect(fixture.nativeElement.querySelector('.history-person').textContent).toContain('Colaborador Europa');
    expect(component().detail()).toBeNull();
  });
  it('mostra a modalidade declarada e identifica registros antigos sem informação',()=>{
    consult(); component().open(item.id);
    http.expectOne('/api/me/work-sessions/session1').flush({...detail,events:[
      {...detail.events[0],workMode:'TRAVEL'}, {...detail.events[0],id:'event2',sequence:2,type:'EXIT',workMode:null}
    ]});fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Modalidade: Viagem');
    expect(fixture.nativeElement.textContent).toContain('Modalidade: Não informado');
  });
  it('separates original markings from the effective approved timeline',()=>{
    consult();component().open(item.id);
    http.expectOne('/api/me/work-sessions/session1').flush({...detail,effective:{state:'CLOSED',workDuration:'PT1H30M',adjusted:true,events:[
      {source:'ORIGINAL',originalEventId:'event1',adjustmentId:null,type:'ENTRY',occurredAtUtc:item.openedAtUtc,recordedTimeZoneId:'America/Sao_Paulo'},
      {source:'ADJUSTMENT',originalEventId:null,adjustmentId:'adjustment1',type:'PAUSE_START',occurredAtUtc:'2026-10-15T00:00:00Z',recordedTimeZoneId:null},
      {source:'ORIGINAL',originalEventId:'event2',adjustmentId:null,type:'EXIT',occurredAtUtc:item.closedAtUtc!,recordedTimeZoneId:'America/Sao_Paulo'}
    ]}});fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Tempo efetivo: 1 h 30 min');
    expect(fixture.nativeElement.textContent).toContain('Linha do tempo efetiva após ajustes');
    expect(fixture.nativeElement.textContent).toContain('Ajuste aprovado');
    expect(fixture.nativeElement.querySelectorAll('.adjusted-event').length).toBe(1);
  });
  it('validates the inclusive range without querying and prevents duplicate queries',()=>{
    component().form.setValue({from:'2026-10-31',to:'2026-10-01'});component().consult();expect(component().fieldError()).toContain('366');http.expectNone('/api/me/work-sessions');
    component().form.setValue({from:'2026-10-01',to:'2026-10-31'});component().consult();component().consult();
    const request=http.expectOne(r=>r.url==='/api/me/work-sessions');expect(request.request.params.get('from')).toBe('2026-10-01');request.flush(page);
    http.expectOne(r=>r.url==='/api/me/work-sessions/summary').flush(summary);
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
    expect(fixture.nativeElement.textContent).toContain('Marcações registradas');expect(fixture.nativeElement.textContent).not.toContain('Fuso no registro:');
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
    consult();component().consult();const summaryRequest=http.expectOne(r=>r.url==='/api/me/work-sessions/summary');
    http.expectOne(r=>r.url==='/api/me/work-sessions').flush({}, {status:503,statusText:'Unavailable'});expect(summaryRequest.cancelled).toBe(true);
    expect(component().result()).toBeNull();expect(component().message()).toContain('preservados');expect(component().busy()).toBe(false);
  });
  it('cancels an old identity request and removes its data on account change',()=>{
    component().consult();const request=http.expectOne(r=>r.url==='/api/me/work-sessions');const summaryRequest=http.expectOne(r=>r.url==='/api/me/work-sessions/summary');
    fixture.componentRef.setInput('user',{...user,userId:'other',employeeId:'other'});fixture.detectChanges();
    expect(request.cancelled).toBe(true);expect(summaryRequest.cancelled).toBe(true);expect(component().result()).toBeNull();expect(component().summary()).toBeNull();expect(component().applied()).toBeNull();
  });
  it('explains empty periods',()=>{
    component().consult();http.expectOne(r=>r.url==='/api/me/work-sessions').flush({...page,items:[],totalElements:0});
    http.expectOne(r=>r.url==='/api/me/work-sessions/summary').flush({...summary,totalSessions:0,closedSessions:0,openSessions:0,adjustedSessions:0,workedDays:0,totalWorkDuration:'PT0S'});fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Nenhuma jornada');expect(fixture.nativeElement.textContent).not.toContain('Total de trabalho encerrado');
  });
  it('shows effective period totals and explains provisional open journeys',()=>{
    consult();expect(fixture.nativeElement.textContent).toContain('Resumo do período');expect(fixture.nativeElement.textContent).toContain('8 h');
    expect(fixture.nativeElement.textContent).toContain('Tempo planejado');expect(fixture.nativeElement.textContent).toContain('+0 h 30 min');
    expect(fixture.nativeElement.textContent).toContain('não representa, por si só, horas extras');
    expect(fixture.nativeElement.textContent).toContain('Jornadas com ajuste');expect(fixture.nativeElement.textContent).toContain('ainda abertas');
  });
  it('does not show a misleading difference when the schedule covers only part of the period',()=>{
    component().consult();http.expectOne(r=>r.url==='/api/me/work-sessions').flush(page);
    http.expectOne(r=>r.url==='/api/me/work-sessions/summary').flush({...summary,balanceSeconds:null,toleranceSeconds:null,withinTolerance:null,scheduledDays:10});fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Não disponível');
    expect(fixture.nativeElement.textContent).toContain('10 de 31 dias');
  });
});
