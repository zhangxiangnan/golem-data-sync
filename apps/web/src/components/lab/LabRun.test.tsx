import { cleanup,render,screen,act } from "@testing-library/react";
import { afterEach,beforeEach,describe,it,expect,vi } from "vitest";
import { LabRun } from "./LabRun";
import { labApi,type Run } from "@/lib/lab-api";
import { template } from "@/lib/lab-config";
vi.mock("next/navigation",()=>({useRouter:()=>({push:vi.fn()})}));
vi.mock("@/lib/lab-api",()=>({labApi:{run:vi.fn(),metrics:vi.fn(),logs:vi.fn(),action:vi.fn()}}));
function run(id:string,status="SAVED"):Run{return {id,experimentId:"e",experimentName:id,externalJobId:"9007199254740993",status,createdAt:"2026-09-24T00:00:00Z",engineVersion:"2.3.13",configDigest:"hash",config:template("basic"),authoringConfig:template("basic"),bindings:{},validation:{valid:true,errors:[],warnings:[],validationLevel:"static",preview:template("basic"),config:template("basic"),tables:[],sourceSchemas:[],dag:{}},observation:{finishedAt:"2026-09-24T00:00:02Z"}};}
afterEach(()=>{cleanup();vi.useRealTimers();vi.clearAllMocks();});beforeEach(()=>{vi.mocked(labApi.metrics).mockResolvedValue([]);vi.mocked(labApi.logs).mockResolvedValue({lines:[]});});
describe("run observations",()=>{
 it("clears transient polling errors after recovery",async()=>{vi.useFakeTimers();vi.mocked(labApi.run).mockRejectedValueOnce(new Error("temporary disconnect")).mockResolvedValue(run("recovered","SUCCEEDED"));render(<LabRun id="recovered"/>);await act(async()=>{});expect(screen.getByText("temporary disconnect")).toBeTruthy();await act(async()=>{await vi.advanceTimersByTimeAsync(2000);});expect(screen.queryByText("temporary disconnect")).toBeNull();expect(screen.getByRole("heading",{name:"recovered"})).toBeTruthy();});
 it("distinguishes savepoint pause and missing metrics",async()=>{vi.mocked(labApi.run).mockResolvedValue(run("saved"));render(<LabRun id="saved"/>);expect(await screen.findByText("保存点暂停")).toBeTruthy();expect(screen.getByRole("button",{name:"从原保存点恢复"})).toBeTruthy();expect(screen.getAllByText("不可用").length).toBeGreaterThan(0);expect(screen.queryByText("正常完成")).toBeNull();});
 it("ignores late responses from the previous route",async()=>{let resolve!:(r:Run)=>void;vi.mocked(labApi.run).mockImplementation(id=>id==="old"?new Promise(r=>{resolve=r;}):Promise.resolve(run("new","SUCCEEDED")));const {rerender}=render(<LabRun id="old"/>);rerender(<LabRun id="new"/>);expect(await screen.findByRole("heading",{name:"new"})).toBeTruthy();await act(async()=>resolve(run("old")));expect(screen.queryByRole("heading",{name:"old"})).toBeNull();});
});
