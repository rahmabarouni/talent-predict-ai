import { Role } from '../../auth/models/user.model';

export namespace UserDto {
    export interface CreateRequest {
        username: string;
        email: string;
        password?: string;
        firstName: string;
        lastName: string;
        department?: string;
        position?: string;
        hireDate?: string;
        profilePictureUrl?: string;
        isActive?: boolean;
        role?: Role;
    }

    export interface UpdateRequest {
        firstName?: string;
        lastName?: string;
        department?: string;
        position?: string;
        hireDate?: string;
        profilePictureUrl?: string;
        isActive?: boolean;
        role?: Role;
        password?: string;
    }
}
