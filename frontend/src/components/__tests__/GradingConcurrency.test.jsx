import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import AssignmentSubmissions from '../../pages/AssignmentSubmissions';
import { assignmentApi } from '../../services/assignmentApi';

vi.mock('../../services/assignmentApi',()=>({assignmentApi:{getAssignmentSessions:vi.fn(),gradeSession:vi.fn()}}));
const row=(id='a',score=50,revision=3)=>({sessionId:id,studentName:id==='a'?'Anna':'Béla',finalScore:50,
    teacherScore:score,teacherComment:'Korábbi megjegyzés',gradingRevision:revision,graded:true,answers:[],finishedAt:'2026-10-26T12:00:00Z'});
beforeEach(()=>{vi.resetAllMocks();assignmentApi.getAssignmentSessions.mockResolvedValue({data:[row()]});});
afterEach(()=>cleanup());
function mount() {
    render(<MemoryRouter initialEntries={['/assignment/task/submissions']}><Routes>
        <Route path="/assignment/:id/submissions" element={<AssignmentSubmissions/>}/>
    </Routes></MemoryRouter>);
}
const conflict=()=>Object.assign(new Error('conflict'),{response:{status:409,data:{message:'Az értékelés közben megváltozott.'}}});

it('preserves the draft and requires comparison before publishing a refreshed version',async()=>{
    const latest={...row('a',70,4),teacherComment:'Másik ablak értékelése'};
    assignmentApi.getAssignmentSessions.mockResolvedValueOnce({data:[row()]}).mockResolvedValueOnce({data:[latest]});
    assignmentApi.gradeSession.mockRejectedValueOnce(conflict()).mockResolvedValue({data:{gradingRevision:5}});
    mount();fireEvent.click(await screen.findByRole('button',{name:/^Módosítás: Anna/}));
    const dialog=await screen.findByRole('dialog');const score=within(dialog).getByLabelText('Végső Pontszám (%)');
    const comment=within(dialog).getByLabelText('Tanári Megjegyzés (Opcionális)');
    fireEvent.change(score,{target:{value:'90'}});fireEvent.change(comment,{target:{value:'Saját visszajelzés'}});
    const publish=within(dialog).getByRole('button',{name:'Eredmény Publikálása'});fireEvent.click(publish);
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('megváltozott');
    expect(assignmentApi.gradeSession).toHaveBeenLastCalledWith('a',{teacherScore:90,teacherComment:'Saját visszajelzés',revision:3});
    fireEvent.click(publish);expect(assignmentApi.gradeSession).toHaveBeenCalledTimes(1);
    const reload=within(dialog).getByRole('button',{name:'Legfrissebb értékelés betöltése'});reload.focus();fireEvent.click(reload);
    expect(await within(dialog).findByRole('region',{name:'Legfrissebb publikált értékelés'})).toHaveTextContent('Másik ablak értékelése');
    expect(reload).toHaveFocus();expect(score).toHaveValue(90);expect(comment).toHaveValue('Saját visszajelzés');
    expect(comment).toHaveAttribute('maxlength','4000');fireEvent.click(publish);
    await waitFor(()=>expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(assignmentApi.gradeSession).toHaveBeenLastCalledWith('a',{teacherScore:90,teacherComment:'Saját visszajelzés',revision:4});
    fireEvent.click(screen.getByRole('button',{name:/^Módosítás: Anna/}));
    fireEvent.click(await screen.findByRole('button',{name:'Eredmény Publikálása'}));
    await waitFor(()=>expect(assignmentApi.gradeSession).toHaveBeenLastCalledWith('a',expect.objectContaining({revision:5})));
});

it('does not let a late reload from a closed dialog replace another students grading form',async()=>{
    assignmentApi.getAssignmentSessions.mockResolvedValueOnce({data:[row(),row('b',60,1)]});
    let finish;assignmentApi.getAssignmentSessions.mockImplementationOnce(()=>new Promise(resolve=>{finish=resolve;}));
    assignmentApi.gradeSession.mockRejectedValueOnce(conflict());
    mount();fireEvent.click(await screen.findByRole('button',{name:/^Módosítás: Anna/}));
    let dialog=await screen.findByRole('dialog');fireEvent.click(within(dialog).getByRole('button',{name:'Eredmény Publikálása'}));
    await within(dialog).findByRole('alert');fireEvent.click(within(dialog).getByRole('button',{name:'Legfrissebb értékelés betöltése'}));
    fireEvent.click(within(dialog).getByRole('button',{name:'Mégse'}));
    await waitFor(()=>expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    fireEvent.click(screen.getByRole('button',{name:/^Módosítás: Béla/}));dialog=await screen.findByRole('dialog');
    await act(async()=>finish({data:[row('a',99,4),row('b',60,1)]}));
    expect(dialog).toHaveAccessibleName('Dolgozat Értékelése: Béla');
    expect(within(dialog).getByLabelText('Végső Pontszám (%)')).toHaveValue(60);
    expect(within(dialog).queryByRole('region',{name:'Legfrissebb publikált értékelés'})).not.toBeInTheDocument();
});
